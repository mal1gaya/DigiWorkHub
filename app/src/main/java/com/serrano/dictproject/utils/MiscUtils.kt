package com.serrano.dictproject.utils

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.auth0.jwt.JWT
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import com.serrano.dictproject.R
import com.serrano.dictproject.api.ApiRepository
import com.serrano.dictproject.datastore.PreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.math.max
import kotlin.math.round

/**
 * Utility Class for processing of different stuffs
 */
object MiscUtils {

    /**
     * Pattern for matching email of users
     */
    const val EMAIL_PATTERN = "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,7}\\b"

    /**
     * Pattern for matching password of users
     */
    const val PASSWORD_PATTERN = "\\b(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{8,}\\b"

    /**
     * Pattern for matching name of users
     */
    const val NAME_PATTERN = "^[a-zA-Z0-9_ ]*\$"

    /**
     * Pattern for matching web urls
     */
    const val URL_PATTERN = "https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{2,256}\\.[a-z]{2,4}\\b([-a-zA-Z0-9@:%_\\+.~#?&//=]*)"

    /**
     * Get the byte array from input stream. Used in File Utilities when converting Uri to base64 encoded string
     *
     * @param[inputStream] InputStream
     *
     * @return ByteArray or byte[]
     */
    @Throws(IOException::class)
    fun getBytes(inputStream: InputStream): ByteArray? {
        val byteBuffer = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var len: Int
        while (inputStream.read(buffer).also { len = it } != -1) {
            byteBuffer.write(buffer, 0, len)
        }
        return byteBuffer.toByteArray()
    }

    /**
     * Generate range of dates from first day of month to last day of month containing the task id and name with its due date matching date in the month.
     *
     * @param[calendarTabIdx] ..., -1 = Previous Month, 0 = Current Month, 1 = Next Month, ...
     * @param[tasks] List of tasks
     *
     * @return Range of dates from first day of month to last day of month containing the task id and name with its due date matching date in the month.
     */
    fun getCalendarData(calendarTabIdx: Int, tasks: List<TaskPartDTO>): List<Calendar> {
        val currentDate = LocalDate.now().plusMonths(calendarTabIdx.toLong())
        val mappedTasks = tasks
            .filter { currentDate.year == it.due.year && currentDate.monthValue == it.due.monthValue }
        val firstDayOfMonth = currentDate.with(TemporalAdjusters.firstDayOfMonth())
        val lastDayOfMonth = currentDate.with(TemporalAdjusters.lastDayOfMonth())
        val firstDayOfMonthView = firstDayOfMonth.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY))
        val lastDayOfMonthView = lastDayOfMonth.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
        val numOfDays = ChronoUnit.DAYS.between(firstDayOfMonthView, lastDayOfMonthView)
        val dates = Stream.iterate(firstDayOfMonthView) {
            it.plusDays(1)
        }.limit(numOfDays)
            .collect(Collectors.toList())
        return dates.map { date ->
            Calendar(
                date = date,
                calendarTasks = mappedTasks
                    .filter { LocalDate.of(it.due.year, it.due.month, it.due.dayOfMonth) == date }
                    .map { CalendarTask(it.taskId, it.title) }
            )

        }
    }

    /**
     * Download the file to the users local device shared storage
     *
     * @param[context] Application Context, needed for content resolver
     * @param[body] The data of file coming from the server
     * @param[fileName] The file name
     * @param[extension] The file extension
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun saveFileToDevice(context: Context, body: ResponseBody, fileName: String, extension: String) {
        val values = ContentValues().apply {
            put(
                MediaStore.Downloads.DISPLAY_NAME,
                fileName + "_" + LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("dd_MM_yyyy_hh_mm_ss")
                ) + "." + extension
            )
        }
        val uri = context.contentResolver.insert(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        )
        context.contentResolver.openOutputStream(uri!!).use {
            it?.write(body.bytes())
        }
        values.clear()
    }

    /**
     * Transform images to imageSize x imageSize pixel size
     *
     * @param[image] The image to transform
     * @param[imageSize] size of image default is 200
     *
     * @return Transformed Image
     */
    fun scaleImage(image: Bitmap, imageSize: Float = 200f): Bitmap {
        val ratio = max(imageSize / image.width, imageSize / image.height)
        val newWidth = round(ratio * image.width).toInt()
        val newHeight = round(ratio * image.height).toInt()
        val scaledBitmap = Bitmap.createScaledBitmap(image, newWidth, newHeight, true)
        return Bitmap.createBitmap(scaledBitmap, 0, 0, 199, 199)
    }

    /**
     * Check the validity and expiration of token
     *
     * @param[authToken] JSON Web token
     *
     * @return True if token is invalid or expired, false otherwise
     */
    fun checkToken(authToken: String): Boolean {
        return try { JWT.decode(authToken).expiresAt.before(Date()) } catch (e: Exception) { true }
    }

    /**
     * Gets the file name from Uri Object
     *
     * @param[context] Application Context, needed for content resolver
     * @param[uri] The Uri Object
     *
     * @return File name
     */
    @SuppressLint("Range")
    fun getFileNameFromUri(context: Context, uri: Uri): String {
        return context.contentResolver.query(uri, null, null, null, null).use { cursor ->
            cursor?.moveToFirst()
            cursor?.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
        } ?: ""
    }

    /**
     * Get the drawable resource/file icons from file extension
     *
     * @param[extension] The file extension
     *
     * @return The id of the drawable resource
     */
    fun getFileIcon(extension: String): Int {
        return when (extension.lowercase()) {
            "pdf" -> R.drawable.pdf
            "json", "jsonl", "jsonc", "json5", "geojson", "cson" -> R.drawable.json_file
            "xml", "rss", "atom", "kml" -> R.drawable.xml
            "html", "xhtml", "mhtml", "mht", "hta", "tmpl" -> R.drawable.html
            "jpg", "jp2", "jfif", "jpg-large", "jps", "jng", "jpeg" -> R.drawable.jpg
            "css", "scss", "less", "sass", "pcss", "styl" -> R.drawable.css
            "js", "jsx", "ts", "coffee", "mjs" -> R.drawable.javascript
            "png", "apng", "mng", "gif", "webp" -> R.drawable.png
            "doc", "docx", "rtf", "odt" -> R.drawable.doc
            "ppt", "pptx", "pptm", "odp", "pps" -> R.drawable.ppt
            "mp3", "m4a", "aac", "wav", "ogg", "flac" -> R.drawable.mp3
            "csv", "tsv", "psv", "ssv", "dsv" -> R.drawable.csv
            "zip", "rar", "7z", "tar", "gz" -> R.drawable.zip
            "svg", "svgz" -> R.drawable.svg
            "aep", "aepx", "aet" -> R.drawable.after_effects
            "mp4", "mov", "avi", "mkv", "wmv", "flv" -> R.drawable.mp4
            "xls", "xlsx", "xlsm", "xlsb", "ods" -> R.drawable.xls
            "txt", "md", "log" -> R.drawable.txt
            "workspace", "abdata", "adobebridge" -> R.drawable.bridge
            "dwt", "dws", "lbi" -> R.drawable.dreamweaver
            "search" -> R.drawable.search
            "exe", "msi", "dll", "bat", "com", "scr" -> R.drawable.exe
            "prproj" -> R.drawable.premiere
            "indd", "indl", "indt", "indb", "inx", "idml", "pmd", "xqx" -> R.drawable.indesign
            "dwg", "dwf", "stl", "igs", "iges" -> R.drawable.dwg
            "eps", "ai", "dxf" -> R.drawable.ai
            "psd", "psb", "tiff", "tif" -> R.drawable.psd
            "aif", "aiff", "aifc" -> R.drawable.audition
            "dbf", "sql" -> R.drawable.dbf
            "mxf", "xmp" -> R.drawable.prelude
            "iso", "img", "bin", "cue", "dmg", "nrg", "mdf", "mds" -> R.drawable.iso
            "fla", "swf", "xfl", "f4v", "swc" -> R.drawable.fla
            else -> R.drawable.file
        }
    }

    /**
     * Wrapper for showing toast in the application
     *
     * @param[context] Application Context
     * @param[message] Nullable String
     */
    fun toast(context: Context, message: String?) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Checks the validity and matches of password and confirm password. Used in signup page.
     *
     * @param[text] Password or Confirm Password
     * @param[other] Password or Confirm Password
     *
     * @return Error message
     */
    fun checkPassword(text: String, other: String): String {
        return when {
            !Regex(PASSWORD_PATTERN).matches(text) -> "Password should have at least one letter and number, and 8-20 characters."
            text != other -> "Password and Confirm Password do not match."
            else -> ""
        }
    }

    /**
     * Wrapper function for api calls that returns a single successful message response and shows toast to any error responses.
     *
     * @param[response] A response from api call
     * @param[onSuccess] A function/action that will be invoked when the response was successful
     * @param[context] For showing toast message
     * @param[preferencesRepository] For authorization check
     * @param[apiRepository] For authorization check
     */
    suspend fun apiEditWrapper(
        response: Resource<Success>,
        onSuccess: suspend () -> Unit,
        context: Context,
        preferencesRepository: PreferencesRepository,
        apiRepository: ApiRepository
    ) {
        try {
            checkAuthentication(context, preferencesRepository, apiRepository)

            toast(
                context,
                when (response) {
                    is Resource.Success -> {
                        onSuccess()
                        response.data?.message
                    }
                    is Resource.ClientError -> response.clientError?.message
                    is Resource.GenericError -> response.genericError
                    is Resource.ServerError -> response.serverError?.error
                }
            )
        } catch (e: Exception) {
            toast(context, e.message)
        }
    }

    /**
     * Wrapper function for api calls that shows toast to any error responses.
     *
     * @param[response] A response from api call
     * @param[onSuccess] A function/action that will be invoked with the response when the response was successful
     * @param[context] For showing toast message
     * @param[preferencesRepository] For authorization check
     * @param[apiRepository] For authorization check
     */
    suspend fun <T> apiAddWrapper(
        response: Resource<T>,
        onSuccess: suspend (T) -> Unit,
        context: Context,
        preferencesRepository: PreferencesRepository,
        apiRepository: ApiRepository
    ) {
        try {
            checkAuthentication(context, preferencesRepository, apiRepository)

            when (response) {
                is Resource.Success -> {
                    onSuccess(response.data!!)
                }
                is Resource.ClientError -> {
                    toast(context, response.clientError?.message)
                }
                is Resource.GenericError -> {
                    toast(context, response.genericError)
                }
                is Resource.ServerError -> {
                    toast(context, response.serverError?.error)
                }
            }
        } catch (e: Exception) {
            toast(context, e.message)
        }
    }

    /**
     * Get the attachment data from the server and download to the users local device
     *
     * @param[fileName] The name of file/attachment when it was uploaded
     * @param[fileServerName] The path of the file/attachment in the server
     * @param[context] Application Context
     * @param[preferencesRepository] The repository that processes the preferences
     * @param[apiRepository] The repository that processes api calls
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun downloadAttachment(
        fileName: String,
        fileServerName: String,
        context: Context,
        preferencesRepository: PreferencesRepository,
        apiRepository: ApiRepository
    ) {
        apiAddWrapper(
            response = apiRepository.downloadAttachment(fileServerName.split("/").last()),
            onSuccess = {
                val fileNameAndExtension = fileName.split(".")
                saveFileToDevice(context, it, fileNameAndExtension.first(), fileNameAndExtension.last())
                toast(context, "Downloading file...")
            },
            context = context,
            preferencesRepository = preferencesRepository,
            apiRepository = apiRepository
        )
    }

    /**
     * Wrapper function for checking authorization of user. If not authorized, the user will automatically log in behind the scene.
     *
     * @param[context] Application Context
     * @param[preferencesRepository] The repository that processes the preferences
     * @param[apiRepository] The repository that processes api calls
     */
    suspend fun checkAuthentication(
        context: Context,
        preferencesRepository: PreferencesRepository,
        apiRepository: ApiRepository
    ) {
        val preferences = preferencesRepository.getData().first()

        if (checkToken(preferences.authToken)) {
            toast(context, "Your token is not valid, refreshing Login.")

            val authResponse = apiRepository.login(
                Login(preferences.email, preferences.password, Firebase.messaging.token.await())
            )

            when (authResponse) {
                is Resource.Success -> {
                    val responseToken = authResponse.data!!.token
                    preferencesRepository.updateAuthToken(responseToken)
                    toast(context, "Login success.")
                }
                is Resource.ClientError -> {
                    throw IllegalStateException(authResponse.clientError?.message)
                }
                is Resource.GenericError -> {
                    throw IllegalStateException(authResponse.genericError)
                }
                is Resource.ServerError -> {
                    throw IllegalStateException(authResponse.serverError?.error)
                }
            }
        }
    }
}