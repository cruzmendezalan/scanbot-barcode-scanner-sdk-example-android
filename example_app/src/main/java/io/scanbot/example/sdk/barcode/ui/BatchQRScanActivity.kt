package io.scanbot.example.sdk.barcode

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.scanbot.example.sdk.barcode.model.BarcodeTypeRepository
import io.scanbot.sdk.SdkLicenseError
import io.scanbot.sdk.barcode.BarcodeDetectorFrameHandler
import io.scanbot.sdk.barcode.entity.BarcodeItem
import io.scanbot.sdk.barcode.entity.BarcodeScanningResult
import io.scanbot.sdk.barcode_scanner.ScanbotBarcodeScannerSDK
import io.scanbot.sdk.camera.FrameHandlerResult
import io.scanbot.sdk.camera.ScanbotCameraView
import kotlin.math.hypot

const val duration: Long = 500

class BatchQRScanActivity : AppCompatActivity(), BarcodeDetectorFrameHandler.ResultHandler {

  private lateinit var cameraView: ScanbotCameraView
  private lateinit var resultView: RecyclerView
  private lateinit var flash: View
  private var flashEnabled = false
  private var barcodeDetectorFrameHandler: BarcodeDetectorFrameHandler? = null
  private val resultAdapter by lazy { ResultAdapter(layoutInflater) }

  override fun onCreate(savedInstanceState: Bundle?) {
    supportRequestWindowFeature(WindowCompat.FEATURE_ACTION_BAR_OVERLAY)
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_batch_qr_camera_view)

    cameraView = findViewById(R.id.camera)
    flash = findViewById(R.id.flash)
    flash.setOnClickListener {
      flashEnabled = !flashEnabled
      cameraView.useFlash(flashEnabled)
    }
    resultView = findViewById(R.id.resultsList)
    resultView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
    resultView.adapter = resultAdapter
  }

  private fun setUpCamera() {
    cameraView.setCameraOpenCallback {
      cameraView.postDelayed({
        // start animation
        // camera is ready
        revealCamera()

        // TODO move that after animation ends
        cameraView.useFlash(flashEnabled)
        cameraView.continuousFocus()
      }, 300)
    }

    val barcodeDetector = ScanbotBarcodeScannerSDK(this).createBarcodeDetector()

    barcodeDetectorFrameHandler = BarcodeDetectorFrameHandler.attach(
      cameraView,
      barcodeDetector
    )

    barcodeDetectorFrameHandler?.setDetectionInterval(1000)
    barcodeDetectorFrameHandler?.addResultHandler(this)

    barcodeDetector.modifyConfig {
      setSaveCameraPreviewFrame(false)
      setBarcodeFormats(BarcodeTypeRepository.selectedTypes.toList())
    }
  }

  private fun revealCamera() {
    // Check if the runtime version is at least Lollipop
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      // get the center for the clipping circle
      val cx = cameraView.width / 2
      val cy = cameraView.height / 2

      // get the final radius for the clipping circle
      val finalRadius = hypot(cx.toDouble(), cy.toDouble()).toFloat()

      // create the animator for this view (the start radius is zero)
      val anim = ViewAnimationUtils.createCircularReveal(cameraView, cx, cy, 0f, finalRadius)
      anim.duration = duration
      // make the view visible and start the animation
      cameraView.visibility = View.VISIBLE
      anim.start()
    } else {
      // set the view to invisible without a circular reveal animation below Lollipop
      cameraView.visibility = View.INVISIBLE
    }
  }

  override fun onResume() {
    super.onResume()
    cameraView.onResume()
    if (ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      // Use onActivityResult to handle permission rejection
      ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.CAMERA),
        REQUEST_PERMISSION_CODE
      )
    }

    setUpCamera()
  }

  override fun onPause() {
    super.onPause()
    cameraView.onPause()
  }

  private fun handleSuccess(result: FrameHandlerResult.Success<BarcodeScanningResult?>) {
    result.value?.let {
      cameraView.post {
        resultAdapter.addBarcodeItems(it.barcodeItems)
      }
    }
  }

  override fun handle(result: FrameHandlerResult<BarcodeScanningResult?, SdkLicenseError>): Boolean {
    if (result is FrameHandlerResult.Success) {
      handleSuccess(result)
    } else {
      cameraView.post {
        Toast.makeText(
          this,
          "License has expired!",
          Toast.LENGTH_LONG
        ).show()
      }
    }
    return false
  }

  companion object {
    private const val REQUEST_PERMISSION_CODE = 200
  }
}

class BarcodeViewHolder(item: View) : RecyclerView.ViewHolder(item) {
  val image: ImageView by lazy { item.findViewById(R.id.image) }
  val barcodeType: TextView by lazy { item.findViewById(R.id.barcodeFormat) }
  val text: TextView by lazy { item.findViewById(R.id.docText) }
}

class ResultAdapter(val layoutInflater: LayoutInflater) :
  RecyclerView.Adapter<BarcodeViewHolder>() {
  private val items: MutableList<BarcodeItem> = mutableListOf()

  fun addBarcodeItems(items: List<BarcodeItem>) {
    // lets check duplicates
    items.forEach { item ->
      var insertedCount = 0
      if (!this.items.any { it.text == item.text }) {
        this.items.add(0, item)
        insertedCount += 1
      }
      notifyItemRangeInserted(0, insertedCount)
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BarcodeViewHolder {
    return BarcodeViewHolder(layoutInflater.inflate(R.layout.barcode_item, parent, false))
  }

  override fun onBindViewHolder(holder: BarcodeViewHolder, position: Int) {
    val item = items.get(position)
    holder.text.text = item.text
    holder.barcodeType.text = item.barcodeFormat.name
    holder.image.setImageBitmap(item.image)
  }

  override fun getItemCount(): Int = items.size

}
