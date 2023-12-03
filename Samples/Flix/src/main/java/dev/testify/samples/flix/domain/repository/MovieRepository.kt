/*
 * The MIT License (MIT)
 *
 * Modified work copyright (c) 2023 ndtp
 * Original work copyright (c) 2023 Andrew Carmichael
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package dev.testify.samples.flix.domain.repository

import android.util.Log
import dev.testify.samples.flix.data.remote.tmdb.TheMovieDbApi
import dev.testify.samples.flix.data.remote.tmdb.TheMovieDbApiFailure
import dev.testify.samples.flix.data.remote.tmdb.TheMovieDbConfigurationApi
import dev.testify.samples.flix.data.remote.tmdb.TheMovieDbUrlResolver
import dev.testify.samples.flix.data.remote.tmdb.entity.Movie
import dev.testify.samples.flix.data.remote.tmdb.entity.Page
import dev.testify.samples.flix.domain.exception.DomainException
import dev.testify.samples.flix.domain.exception.NetworkUnavailableException
import dev.testify.samples.flix.domain.exception.RemoteApiFailedException
import dev.testify.samples.flix.domain.exception.UnknownDomainException
import dev.testify.samples.flix.domain.model.MovieCreditsDomainModel
import dev.testify.samples.flix.domain.model.MovieDomainModel
import dev.testify.samples.flix.domain.model.MovieReleaseDatesDomainModel
import dev.testify.samples.flix.domain.model.mapper.DataLayerEntityToDomainModelMapper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MovieRepository @Inject constructor(
    private val remoteApi: TheMovieDbApi,
    private val configurationApi: TheMovieDbConfigurationApi,
    private val dataModelEntityToDomainModelMapper: DataLayerEntityToDomainModelMapper,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO
)  {

    companion object {
        val LOG_TAG = MovieRepository::class.simpleName
    }

    suspend fun getMostTrendingMovie(): Result<MovieDomainModel> = withContext(coroutineDispatcher) {
        val apiConfigurationDeferred = async { configurationApi.getApiConfiguration() }
        remoteApi.getTrending(TheMovieDbApi.MediaType.Movie, TheMovieDbApi.TimeWindow.Day).fold(
            onSuccess = { page ->
                val apiConfiguration = apiConfigurationDeferred.await()
                val domainModel = page.results?.firstOrNull()?.let {
                    dataModelEntityToDomainModelMapper.map(it, TheMovieDbUrlResolver(apiConfiguration.baseUrl, apiConfiguration.headlineImageSizeKey))
                }
                domainModel?.let { Result.success(it) } ?: Result.failure(onFailedToConvertToDomainModel())
            },
            onFailure = { throwable ->
                Result.failure(throwable.toDomainException())
            }
        )
    }

    suspend fun getTrendingMovies(): Result<List<MovieDomainModel>> {
        return getPageOfMovies { getTrending(TheMovieDbApi.MediaType.Movie, TheMovieDbApi.TimeWindow.Day) }
    }

    suspend fun getMoviesNowPlaying(): Result<List<MovieDomainModel>> {
        return getPageOfMovies { getMoviesNowPlaying() }
    }

    suspend fun getUpcomingMovies(): Result<List<MovieDomainModel>> {
        return getPageOfMovies { getUpcomingMovies() }
    }

    private suspend fun getPageOfMovies(
        endpointSelector: suspend TheMovieDbApi.() -> Result<Page<Movie>>
    ): Result<List<MovieDomainModel>> = withContext(coroutineDispatcher) {
        val apiConfigurationDeferred = async { configurationApi.getApiConfiguration() }
        endpointSelector(remoteApi).fold(
            onSuccess = { page ->
                val apiConfiguration = apiConfigurationDeferred.await()
                val domainModels = page.results?.mapNotNull { dataModelEntityToDomainModelMapper.map(it, TheMovieDbUrlResolver(apiConfiguration.baseUrl, apiConfiguration.thumbnailImageSizeKey)) }
                domainModels?.let { Result.success(it) }
                    ?: Result.failure(onFailedToConvertToDomainModel())
            },
            onFailure = { throwable ->
                Result.failure(throwable.toDomainException())
            }
        )
    }

    suspend fun getMovieDetails(movieId: Int): Result<MovieDomainModel> = withContext(coroutineDispatcher) {
        val apiConfigurationDeferred = async { configurationApi.getApiConfiguration() }
        remoteApi.getMovieDetails(movieId).fold(
            onSuccess = { dataModel ->
                val apiConfiguration = apiConfigurationDeferred.await()
                val domainModel = dataModelEntityToDomainModelMapper.map(dataModel, TheMovieDbUrlResolver(apiConfiguration.baseUrl, apiConfiguration.thumbnailImageSizeKey))
                domainModel?.let { Result.success(it) }
                    ?: Result.failure(onFailedToConvertToDomainModel())
            },
            onFailure = { throwable ->
                Result.failure(throwable.toDomainException())
            }
        )
    }

    suspend fun getMovieReleaseDates(movieId: Int): Result<MovieReleaseDatesDomainModel> = withContext(coroutineDispatcher) {
        remoteApi.getMovieReleaseDates(movieId).fold(
            onSuccess = { movieReleaseDatesDataModel ->
                val domainModel = dataModelEntityToDomainModelMapper.map(movieReleaseDatesDataModel)
                domainModel?.let {
                    Result.success(it)
                } ?: Result.failure(onFailedToConvertToDomainModel())
            },
            onFailure = { throwable ->
                Result.failure(throwable.toDomainException())
            }
        )
    }

    suspend fun getMovieCredits(movieId: Int): Result<MovieCreditsDomainModel> = withContext(coroutineDispatcher) {
        val apiConfigurationDeferred = async { configurationApi.getApiConfiguration() }
        remoteApi.getMovieCredits(movieId).fold(
            onSuccess = { movieCredits ->
                val apiConfiguration = apiConfigurationDeferred.await()
                val urlResolver = TheMovieDbUrlResolver(apiConfiguration.baseUrl, apiConfiguration.thumbnailImageSizeKey)
                val domainModel = dataModelEntityToDomainModelMapper.map(movieCredits, urlResolver)
                Result.success(domainModel)
            },
            onFailure = { throwable ->
                Result.failure(throwable.toDomainException())
            }
        )
    }

    private fun Throwable.toDomainException(): DomainException {
        return when (this) {
            is TheMovieDbApiFailure.RequestFailure -> onRequestFailure(this)
            is TheMovieDbApiFailure.ResponseFailure -> onResponseFailure(this)
            is TheMovieDbApiFailure.NetworkFailure -> onNetworkFailure(this)
            is TheMovieDbApiFailure.SerializationFailure -> onSerializationFailure(this)
            else -> onUnknownFailure(this)
        }
    }

    private fun onRequestFailure(throwable: Throwable): DomainException {
        val message = "There was a problem with the request"
        Log.e(LOG_TAG, message)
        return RemoteApiFailedException(message, throwable)
    }

    private fun onResponseFailure(throwable: Throwable): DomainException {
        val message = "There was a problem with the response"
        Log.e(LOG_TAG, message)
        return RemoteApiFailedException(message, throwable)
    }

    private fun onNetworkFailure(throwable: Throwable): DomainException {
        val message = "Network unavailable"
        Log.e(LOG_TAG, message)
        return NetworkUnavailableException(message, throwable)
    }

    private fun onSerializationFailure(throwable: Throwable): DomainException {
        val message = "There was a problem deserializing the network response"
        Log.e(LOG_TAG, message)
        return DomainException(message, throwable)
    }

    private fun onFailedToConvertToDomainModel(): DomainException {
        val message = "$LOG_TAG failed to convert data source to domain model"
        Log.e(LOG_TAG, message)
        return DomainException(message)
    }

    private fun onUnknownFailure(throwable: Throwable): DomainException {
        val message = "$LOG_TAG unknown error"
        Log.e(LOG_TAG, "$message ${throwable.message}")
        return UnknownDomainException(message, throwable)
    }
}
