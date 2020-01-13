package com.isabel.feed.imagepicker

import android.annotation.TargetApi
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.isabel.feed.BuildConfig
import com.isabel.feed.R
import com.isabel.feed.utils.Constants
import com.isabel.feed.utils.getUserId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.collections.ArrayList

class ImagePicker(private val context: Context, private val imagePickerInterface: ImagePickerInterface?) {
    private var array = arrayOf(context.getString(R.string.piker_cam), context.getString(R.string.piker_gall))
    private val title = context.getString(R.string.piker_choseimage)
    private var onGetBitmapListener: OnGetBitmapListener? = null
    private var uri: Uri? = null

    /**
     * We can get image in file format using this method.
     *
     * @return
     */
    init {
        arrayOf(context.getString(R.string.piker_cam), context.getString(R.string.piker_gall))
    }

    var imageFile: File? = null
        set

    fun createImageChooser(isMultiPleImage: Boolean = false) {
        val list = Arrays.asList(*array)
        val cs = list.toTypedArray<CharSequence>()

        val dialog = AlertDialog.Builder(context, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
        dialog.setTitle(title)

        dialog.setSingleChoiceItems(cs, -1) { dialog, which ->
            dialog.dismiss()

            when (which) {
                0 -> {
                    imagePickerInterface?.handleIntent(generateCameraPickerIntent(), true)
                }
                1 -> {
                    imagePickerInterface?.handleIntent(generateGalleryPickerIntent(isMultiPleImage), false)
                }

            }
        }

        val alertDialog = dialog.create()
        if (!alertDialog.isShowing) {
            alertDialog.show()
        }
    }

    /**
     * Generate Intent to open camera
     *
     * @return : [Intent] to open camera.
     */
    private fun generateCameraPickerIntent(): Intent {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        imageFile = File(createOrGetProfileImageDir(context), getFileName(".jpg"))

        uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", imageFile!!)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        val resInfoList = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName;
            context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        return intent
    }

    /**
     * Generate intent to open gallery to choose image
     *
     *
     * :[Context] to create intent
     *
     * @return : [Intent] to open gallery
     */
    private fun generateGalleryPickerIntent(multiPleImage: Boolean): Intent {

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        intent.action = Intent.ACTION_GET_CONTENT
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.type = "image/*"

        if (multiPleImage) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        return intent
    }

    private fun sdCardMounted(): Boolean {
        var isMediaMounted = false
        val state = Environment.getExternalStorageState()
        if (Environment.MEDIA_MOUNTED == state) {
            isMediaMounted = true
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY == state) {
            isMediaMounted = false
        } else if (Environment.MEDIA_CHECKING == state) {
            isMediaMounted = false
        } else if (Environment.MEDIA_NOFS == state) {
            isMediaMounted = false
        } else if (Environment.MEDIA_REMOVED == state) {
            isMediaMounted = false
        } else if (Environment.MEDIA_SHARED == state) {
            isMediaMounted = false
        } else if (Environment.MEDIA_UNMOUNTABLE == state) {
            isMediaMounted = false
        } else if (Environment.MEDIA_UNMOUNTED == state) {
            isMediaMounted = false
        }
        return isMediaMounted
    }

    fun preventAutoRotate(bitmap: Bitmap?, orientation: Int): Bitmap? {

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_NORMAL -> return bitmap
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        try {
            val bmRotated = Bitmap.createBitmap(bitmap!!, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            return bmRotated
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            return null
        }

    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    fun getPathFromURI(uri: Uri): String? {

        val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
                val type = split[0]

                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }

                // TODO handle non-primary volumes
            } else if (isDownloadsDocument(uri)) {

                val id = DocumentsContract.getDocumentId(uri)
                try {
                    val contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"),
                            java.lang.Long.valueOf(id))
                    return getDataColumn(context, contentUri, null, null)

                } catch (e: NumberFormatException) {
                    return id.replace("raw:", "")
                }

            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
                val type = split[0]

                var contentUri: Uri? = null
                if ("image" == type) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else if ("video" == type) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else if ("audio" == type) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

                val selection = "_id=?"
                val selectionArgs = arrayOf<String>(split[1])

                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {

            // Return the remote address
            return if (isGooglePhotosUri(uri)) uri.lastPathSegment else getDataColumn(context, uri, null, null)

        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }

        return null
    }

    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    private fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }

    private fun getDataColumn(context: Context, uri: Uri?, selection: String?, selectionArgs: Array<String>?): String? {

        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)

        try {
            cursor = context.contentResolver.query(uri!!, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(index)
            }
        } finally {
            if (cursor != null)
                cursor.close()
        }
        return null
    }


    private fun getProfileDirectory(context: Context): File {

        val rootDir = File(context.getExternalFilesDir(null), "")

        if (!rootDir.exists()) {
            rootDir.mkdir()
        }

        val dir = File("$rootDir/profile/")

        if (!dir.exists()) {
            dir.mkdir()
        }
        return dir
    }


    private fun getMultiImageBitmap(uriList: ArrayList<Uri?>) {
        try {
            val imagePathList = ArrayList<Uri>()
            val fileExtensionList = ArrayList<String>()
            uriList.forEach {

                if (it != null) {
                    imagePathList.add(it)
                    fileExtensionList.add(".jpeg")
                }

            }
            saveMultipeImagesWithBitmap(imagePathList, fileExtensionList)

        } catch (e: Exception) {
            e.printStackTrace()
            onGetBitmapListener?.getMultiImageImagePath(null)
        }

    }

    private fun getBitmap(uri: Uri?, isFromGallery: Boolean) {
        try {
            if (isFromGallery) {
                val imagePath = getPathFromURI(uri!!)
                if (imagePath != null && imagePath.isNotEmpty()) {
                    val index = imagePath.lastIndexOf(".")
                    val fileExtension = if (index != -1) {
                        imagePath.substring(index)
                    } else {
                        ".jpeg"
                    }
                    imageFile = File(imagePath)
                    if (imageFile!!.exists()) {
                        saveBitmap(uri, fileExtension)
                    }
                } else {
                    onGetBitmapListener!!.onGetBitmap(null, "")
                }
            } else {
                if (imageFile?.exists() == true) {
                    OptimizeBitmapTask()
                } else {
                    onGetBitmapListener!!.onGetBitmap(null, "")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onGetBitmapListener!!.onGetBitmap(null, "")
        }

    }

    interface OnGetBitmapListener {
        fun onGetBitmap(bitmap: Bitmap?, imagepath: String?) {}
        fun getImagePath(imagepath: String?) {}
        fun getMultiImageImagePath(imagePathList: ArrayList<String>?) {}
        fun imageSelectionError(error: Int) {}
    }

    private fun getFileName(fileExtension: String): String {
        return "${getUserId()}_${System.currentTimeMillis()}$fileExtension"
    }

    private fun saveBitmap(path: Uri, fileExtension: String) {

        val finalImageList = ArrayList<String>()

        CoroutineScope(Dispatchers.IO).launch {
            try {

                val destinationFile = File(context.getExternalFilesDir(null), getFileName(fileExtension))

                val filepath = destinationFile.absolutePath
                if (!destinationFile.exists())
                    destinationFile.createNewFile()

                var fos: FileOutputStream? = null

                fos = FileOutputStream(destinationFile)

                val bitmap = BitmapFactory.decodeStream(path.let {
                    context.contentResolver.openInputStream(
                            it
                    )
                })

                bitmap?.compress(Bitmap.CompressFormat.JPEG, 100, fos)

                val file = Uri.fromFile(destinationFile)
                finalImageList.add(file.lastPathSegment.toString())

                withContext(Dispatchers.Main) {
                    onGetBitmapListener?.getImagePath(imageFile!!.absolutePath)
                    onGetBitmapListener?.onGetBitmap(null, imageFile!!.absolutePath)

                }


            } catch (e: Exception) {
                e.printStackTrace()
            }

        }


    }


//    private inner class SaveBitmapAsyncTask(private val path: String, private val fileExtension: String) : AsyncTask<Void, Void, Void>() {
//        private var bitmap: Bitmap? = null
//
//        override fun doInBackground(vararg params: Void): Void? {
//            try {
//                val sourceFile = File(path)
//                val destinationFile = File(createOrGetProfileImageDir(context), getFileName(fileExtension))
//                if (destinationFile.exists())
//                    destinationFile.delete()
//
//
//                val src = FileInputStream(sourceFile).channel
//                val dst = FileOutputStream(destinationFile).channel
//                dst.transferFrom(src, 0, src.size())
//                src.close()
//                dst.close()
//                imageFile = destinationFile
//
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//
//            return null
//        }
//
//        override fun onPostExecute(s: Void?) {
//            super.onPostExecute(s)
//            if (onGetBitmapListener != null) {
//                onGetBitmapListener!!.getImagePath(imageFile!!.absolutePath)
//                onGetBitmapListener!!.onGetBitmap(null, imageFile!!.absolutePath)
//            }
//
//        }
//    }


    fun saveMultipeImagesWithBitmap(imagePathList: ArrayList<Uri>, fileExtension: ArrayList<String>) {
        val finalImageList = ArrayList<String>()

        CoroutineScope(Dispatchers.IO).launch {
            try {

                imagePathList.forEachIndexed { index, path ->

                    val destinationFile = File(context.getExternalFilesDir(null), getFileName(fileExtension[index]))

                    val filepath = destinationFile.absolutePath
                    if (!destinationFile.exists())
                        destinationFile.createNewFile()

                    var fos: FileOutputStream? = null

                    fos = FileOutputStream(destinationFile)

                    val bitmap = BitmapFactory.decodeStream(path.let {
                        context.contentResolver.openInputStream(
                                it
                        )
                    })

                    bitmap?.compress(Bitmap.CompressFormat.JPEG, 100, fos)

                    val file = Uri.fromFile(destinationFile)
                    finalImageList.add(file.lastPathSegment.toString())


                    Log.i("TAGSS", "file path is ==== $filepath")
                }

                withContext(Dispatchers.Main) {
                    onGetBitmapListener?.getMultiImageImagePath(imagePathList = finalImageList)

                }


            } catch (e: Exception) {
                e.printStackTrace()
            }

        }

    }


//    private inner class SaveMultiBitmapAsyncTask(private val imagePathList: ArrayList<String?>, private val fileExtension: ArrayList<String>) : AsyncTask<Void, Void, Void>() {
//
//        val finalImageList = ArrayList<String>()
//        override fun doInBackground(vararg params: Void): Void? {
//            try {
//                imagePathList.forEachIndexed { index, path ->
//                    val sourceFile = File(path)
//                    val destinationFile = File(createOrGetProfileImageDir(context), getFileName(fileExtension[index]))
//                    if (destinationFile.exists())
//                        destinationFile.delete()
//
//                    val src = FileInputStream(sourceFile).channel
//                    val dst = FileOutputStream(destinationFile).channel
//                    dst.transferFrom(src, 0, src.size())
//                    src.close()
//                    dst.close()
//                    val file = Uri.fromFile(destinationFile)
//                    finalImageList.add(file.lastPathSegment.toString())
//                }
//
//
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//
//            return null
//        }
//
//        override fun onPostExecute(s: Void?) {
//            super.onPostExecute(s)
//            onGetBitmapListener?.getMultiImageImagePath(imagePathList = finalImageList)
//        }
//    }

    fun setOnGetBitmapListener(onGetBitmapListener: OnGetBitmapListener) {
        this.onGetBitmapListener = onGetBitmapListener
    }

    private fun getOptimizedBitmap(path: String?): Bitmap? {
        try {
            val bitmap = BitmapFactory.decodeFile(path)


            var scaledBitmap: Bitmap?
            if (bitmap.width > 1500 || bitmap.height > 1500) {
                scaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
            } else
                scaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width, bitmap.height, true)

            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)

            scaledBitmap = preventAutoRotate(scaledBitmap, orientation)

            val out = FileOutputStream(File(path!!))
            scaledBitmap!!.compress(Bitmap.CompressFormat.JPEG, 100, out)
            out.close()

            return scaledBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

    }

    fun onActivityResult(requestCode: Int, data: Intent?, isMultiPleImage: Boolean = false, imageCount: Int? = 0) {

        if (!isMultiPleImage) {
            if (requestCode == CAMERA_REQUEST) {
                if (isMaxImageSelected(imageCount)) {
                    onGetBitmapListener?.imageSelectionError(R.string.error_max_image_select)
                } else {
                    getBitmap(null, false)
                }
            } else if (requestCode == GALLERY_REQUEST) {
                if (isMaxImageSelected(imageCount)) {
                    onGetBitmapListener?.imageSelectionError(R.string.error_max_image_select)
                } else {
                    if (data != null && data.data != null) {
                        this.uri = data.data
                        getBitmap(data.data, true)
                    }
                }
            }
        } else {
            if (data?.clipData != null) {
                if ((data.clipData?.itemCount ?: 0) + ((imageCount
                                ?: 0) - 1) <= Constants.MAX_IMAGE_SELECT) {
                    val imageList = ArrayList<Uri>()
                    for (i in 0 until (data.clipData?.itemCount ?: 0)) {
                        val imageUri = data.clipData!!.getItemAt(i).uri
                        imageList.add(imageUri)
                    }
                    getMultiImageBitmaps(imageList)
                } else {
                    onGetBitmapListener?.imageSelectionError(R.string.error_max_image_select)
                }

            } else if (data?.data != null) {
                if (isMaxImageSelected(imageCount)) {
                    onGetBitmapListener?.imageSelectionError(R.string.error_max_image_select)
                } else {
                    arrayListOf(data.data)?.let { getMultiImageBitmap(it) }
                }

            }
        }
    }

    private fun getMultiImageBitmaps(uriList: ArrayList<Uri>) {


        try {
            val imagePathList = ArrayList<Uri>()
            val fileExtensionList = ArrayList<String>()
            uriList.forEach {
                if (it != null) {
                    imagePathList.add(it)
                    fileExtensionList.add(".jpeg")
                }

            }
            saveMultipeImagesWithBitmap(imagePathList, fileExtensionList)

        } catch (e: Exception) {
            e.printStackTrace()
            onGetBitmapListener?.getMultiImageImagePath(null)
        }


    }

    private fun isMaxImageSelected(imageCount: Int?) = (imageCount ?: 0) - 1 >= 5


    private fun OptimizeBitmapTask() {
        if (onGetBitmapListener != null) {
            val file = Uri.fromFile(imageFile)
            onGetBitmapListener!!.onGetBitmap(null, imageFile!!.absolutePath)
            onGetBitmapListener!!.getImagePath(file.lastPathSegment)
        }
    }

    companion object {
        val GALLERY_REQUEST = 123
        val CAMERA_REQUEST = 456
        val SELECT_PICTURES = 789

        fun createOrGetProfileImageDir(context: Context): File {


            return File(context.getExternalFilesDir(null),"")
        }

    }
}

