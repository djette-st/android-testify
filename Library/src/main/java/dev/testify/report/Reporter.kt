/*
 * The MIT License (MIT)
 *
 * Modified work copyright (c) 2022 ndtp
 * Original work copyright (c) 2021 Shopify Inc.
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
package dev.testify.report

import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.test.platform.app.InstrumentationRegistry
import dev.testify.ScreenshotRule
import dev.testify.TestDescription
import dev.testify.core.DEFAULT_NAME_FORMAT
import dev.testify.core.DeviceStringFormatter
import dev.testify.core.formatDeviceString
import dev.testify.core.getDeviceDescription
import dev.testify.internal.annotation.ExcludeFromJacocoGeneratedReport
import dev.testify.output.Destination
import dev.testify.output.PNG_EXTENSION
import dev.testify.output.getDestination
import dev.testify.output.getFileRelativeToRoot
import org.junit.Assert.assertTrue
import java.io.File

/**
 * [Reporter] class creates a YAML report for a test run.
 *
 * Creates a `report.yml` file in /data/data/{{com.your.package}}/app_testify
 *
 * To enable, set enableReporter to true when initializing [ScreenshotRule]
 * Or, <meta-data android:name="testify-reporter" android:value="true" /> in the AndroidManifest
 *
 * Use `./gradlew :
 *
 */
internal open class Reporter protected constructor(
    private val context: Context,
    private val session: ReportSession
) {

    /**
     * Builder for the YAML report
     */
    @VisibleForTesting
    internal val builder = StringBuilder()

    /**
     * Description of the current test
     */
    private lateinit var testDescription: TestDescription

    /**
     * Creates a unique session ID for the given test run
     */
    fun identifySession(instrumentation: Instrumentation) {
        session.identifySession(instrumentation)
    }

    /**
     * Called by [ScreenshotRule.apply] when a new test case starts
     * Records the test entry
     */
    fun startTest(description: TestDescription) {
        testDescription = description
        session.addTest()

        builder.appendLine("- test:", indent = 4)
        builder.appendLine("name: ${description.methodName}", indent = 8)
        builder.appendLine("class: ${description.testClass.simpleName}", indent = 8)
        builder.appendLine("package: ${description.testClass.`package`?.name}", indent = 8)
    }

    /**
     * Called by [ScreenshotRule.assertSame]
     * At this point in the execution, Testify can correctly identify the baseline path as all
     * modifications have been applied
     */
    fun captureOutput() {
        builder.appendLine("baseline_image: assets/${getBaselinePath()}", indent = 8)
        builder.appendLine("test_image: ${getOutputPath()}", indent = 8)
    }

    /**
     * Records a passing test
     */
    fun pass() {
        session.pass()
        builder.appendLine("status: ${TestStatus.PASS.name}", indent = 8)
    }

    /**
     * Records a skipped or ignored test
     */
    fun skip() {
        session.skip()
        builder.appendLine("status: ${TestStatus.SKIP.name}", indent = 8)
    }

    /**
     * Records that a test has failed and the cause of the failure
     */
    fun fail(throwable: Throwable) {
        session.fail()
        builder.appendLine("status: ${TestStatus.FAIL.name}", indent = 8)
        val cause = describeErrorCause(throwable)
        builder.appendLine("cause: ${cause.name}", indent = 8)
        builder.appendLine("description: \"${cause.description}\"", indent = 8)
    }

    /**
     * Mark the end of the test
     */
    fun endTest() {
        val outputFile = getReportFile()
        initializeYaml(outputFile)
        writeToFile(builder, outputFile)
    }

    /**
     * Finalize the report.yml file
     */
    @ExcludeFromJacocoGeneratedReport
    fun finalize() =
        getDestination().finalize()

    /**
     * Write the report to the given file
     */
    @ExcludeFromJacocoGeneratedReport
    @VisibleForTesting
    open fun writeToFile(builder: StringBuilder, file: File) =
        file.appendText(builder.toString())

    /**
     * Append a line to the builder with the given indent
     */
    private fun StringBuilder.appendLine(value: String, indent: Int): StringBuilder =
        append("".padStart(indent)).appendLine(value)

    /**
     * Get the path to the baseline image
     */
    @VisibleForTesting
    internal open fun getBaselinePath(): String =
        getFileRelativeToRoot(
            subpath = getDeviceDescription(context),
            fileName = testDescription.methodName,
            extension = PNG_EXTENSION
        )

    /**
     * Get the path to the output image
     */
    @get:ExcludeFromJacocoGeneratedReport
    private val Context.fileName: String
        get() = formatDeviceString(
            DeviceStringFormatter(
                this,
                testDescription.nameComponents
            ),
            DEFAULT_NAME_FORMAT
        )

    /**
     * Get the path to the report YAML file
     */
    @ExcludeFromJacocoGeneratedReport
    @VisibleForTesting
    internal open fun getOutputPath(): String =
        getDestination(context, context.fileName).description

    /**
     * Get the arguments passed to the test
     */
    @ExcludeFromJacocoGeneratedReport
    @VisibleForTesting
    internal open fun getEnvironmentArguments(): Bundle =
        InstrumentationRegistry.getArguments()

    /**
     * Construct the destination object for the report file
     */
    private fun getDestination(): Destination = getDestination(
        context = context,
        fileName = "report",
        extension = ".yml",
        root = "testify",
        customKey = ""
    )

    /**
     * Get the report file
     */
    @VisibleForTesting
    internal open fun getReportFile(): File {
        val destination = getDestination()
        assertTrue(
            "Could not create reporter destination ${destination.description}",
            destination.assureDestination(context)
        )
        return destination.file
    }

    /**
     * Get the number of lines in the header
     */
    @VisibleForTesting
    internal val headerLineCount: Int
        get() = listOf(HEADER, "tests").size + session.sessionLineCount

    /**
     * Insert the header into the builder
     */
    @VisibleForTesting
    internal fun insertHeader() {
        builder.insert(0, "- tests:\n")
        session.insertSessionInfo(builder)
        builder.insert(0, "$HEADER\n")
    }

    /**
     * Initialize the YAML file
     */
    @VisibleForTesting
    internal fun initializeYaml(file: File) {
        if (!file.exists()) {
            insertHeader()
        } else {
            if (session.isEqual(file)) {
                updateExistingFile(file)
            } else {
                // If it is a different session, clear and start a new session
                clearFile(file)
                insertHeader()
            }
        }
    }

    /**
     * Clear the contents of the given file
     */
    @ExcludeFromJacocoGeneratedReport
    @VisibleForTesting
    internal open fun clearFile(file: File) {
        file.writeText("")
    }

    /**
     * Update the existing file
     *
     * Initialize a session from the given file, updated it with the current session information and test results,
     * then update the file.
     */
    private fun updateExistingFile(file: File) {
        session.initFromFile(file)
        val lines = readBodyLines(file)

        clearFile(file)

        insertHeader()
        lines.forEach {
            builder.appendLine(it)
        }
    }

    /**
     * Read the body lines from the given file
     */
    @ExcludeFromJacocoGeneratedReport
    @VisibleForTesting
    internal open fun readBodyLines(file: File): List<String> {
        return file.readLines().drop(headerLineCount)
    }

    companion object {
        private const val HEADER = "---"

        /**
         * Factory method to create an instance of [Reporter]
         */
        fun create(context: Context, session: ReportSession = ReportSession()): Reporter =
            Reporter(context, session)
    }
}
