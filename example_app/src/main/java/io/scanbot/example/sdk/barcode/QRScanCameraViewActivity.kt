package io.scanbot.example.sdk.barcode

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import io.scanbot.barcodescanner.model.DEMedicalPlan.DEMedicalPlanDocument
import io.scanbot.barcodescanner.model.boardingPass.BoardingPassDocument
import io.scanbot.sdk.SdkLicenseError
import io.scanbot.sdk.barcode.BarcodeAutoSnappingController
import io.scanbot.sdk.barcode.BarcodeDetectorFrameHandler
import io.scanbot.sdk.barcode.entity.BarcodeItem
import io.scanbot.sdk.barcode.entity.BarcodeScanningResult
import io.scanbot.sdk.barcode_scanner.ScanbotBarcodeScannerSDK
import io.scanbot.sdk.camera.FrameHandlerResult
import net.doo.snap.camera.PictureCallback
import net.doo.snap.camera.ScanbotCameraView

class QRScanCameraViewActivity : AppCompatActivity(), BarcodeDetectorFrameHandler.ResultHandler,
    PictureCallback {

    private var cameraView: ScanbotCameraView? = null
    private var resultView: ImageView? = null

    internal var flashEnabled = false
    private var toast: Toast? = null
    private var barcodeDetectorFrameHandler: BarcodeDetectorFrameHandler? = null


    override fun onCreate(savedInstanceState: Bundle?) {

        supportRequestWindowFeature(WindowCompat.FEATURE_ACTION_BAR_OVERLAY)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_camera_view)

        supportActionBar!!.hide()

        cameraView = findViewById(R.id.camera)
        resultView = findViewById(R.id.result)

        cameraView!!.setCameraOpenCallback {
            cameraView!!.postDelayed({
                cameraView!!.useFlash(flashEnabled)
                cameraView!!.continuousFocus()
            }, 300)
        }

        barcodeDetectorFrameHandler = BarcodeDetectorFrameHandler.attach(
            cameraView!!,
            ScanbotBarcodeScannerSDK(this).barcodeDetector()
        )

        barcodeDetectorFrameHandler!!.setDetectionInterval(1000)
        barcodeDetectorFrameHandler!!.addResultHandler(this)
        barcodeDetectorFrameHandler!!.saveCameraPreviewFrame(true)

        val barcodeAutoSnappingController =
            BarcodeAutoSnappingController.attach(cameraView!!, barcodeDetectorFrameHandler!!)
        barcodeAutoSnappingController.setSensitivity(1f)
        cameraView!!.addPictureCallback(this)

        val scanbotSDK = ScanbotBarcodeScannerSDK(this)

    }

    override fun onResume() {
        super.onResume()
        cameraView!!.onResume()
    }

    override fun onPause() {
        super.onPause()
        cameraView!!.onPause()
    }


    private fun handleSuccess(result: FrameHandlerResult.Success<BarcodeScanningResult?>) {
        val detectedBarcodes = result.value
        BarcodeResultRepository.barcodeScanningResult = detectedBarcodes
        val intent = Intent(this, BarcodeResultActivity::class.java)
        startActivity(intent)
        finish()
    }


    override fun onPictureTaken(image: ByteArray, imageOrientation: Int) {
        val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size)

        val matrix = Matrix()
        matrix.setRotate(imageOrientation.toFloat(), bitmap.width / 2f, bitmap.height / 2f)
        val resultBitmap =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)

        resultView!!.post {
            resultView!!.setImageBitmap(resultBitmap)
            cameraView!!.continuousFocus()
            cameraView!!.startPreview()
        }
    }


    override fun handle(result: FrameHandlerResult<BarcodeScanningResult?, SdkLicenseError>): Boolean {
        if (result is FrameHandlerResult.Success) {
            handleSuccess(result)
        } else {
            cameraView?.post {
                Toast.makeText(
                    this,
                    "License has expired!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        return false;
    }

}