
package com.punchthrough.blestarterappandroid

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.RemoteViews
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.punchthrough.blestarterappandroid.ble.ConnectionEventListener
import com.punchthrough.blestarterappandroid.ble.ConnectionManager
import com.punchthrough.blestarterappandroid.ble.isIndicatable
import com.punchthrough.blestarterappandroid.ble.isNotifiable
import com.punchthrough.blestarterappandroid.ble.isReadable
import com.punchthrough.blestarterappandroid.ble.isWritable
import com.punchthrough.blestarterappandroid.ble.isWritableWithoutResponse
import kotlinx.android.synthetic.main.activity_ble_operations.textView3
import org.jetbrains.anko.alert
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID


@SuppressLint("LogNotTimber")
class BleOperationsActivity : AppCompatActivity() {
    private lateinit var device: BluetoothDevice
    private val dateFormatter = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)

    lateinit var notificationChannel: NotificationChannel
    lateinit var notificationManager: NotificationManager
    lateinit var builder: Notification.Builder
    private val channelId = "12345"
    private val description = "Test Notification"


    private val characteristics by lazy {
        ConnectionManager.servicesOnDevice(device)?.flatMap { service ->
            service.characteristics ?: listOf()
        } ?: listOf()
    }
    private val characteristicProperties by lazy {
        characteristics.map { characteristic ->
            characteristic to mutableListOf<CharacteristicProperty>().apply {
                if (characteristic.isNotifiable()) add(CharacteristicProperty.Notifiable)
                if (characteristic.isIndicatable()) add(CharacteristicProperty.Indicatable)
                if (characteristic.isReadable()) add(CharacteristicProperty.Readable)
                if (characteristic.isWritable()) add(CharacteristicProperty.Writable)
                if (characteristic.isWritableWithoutResponse()) {
                    add(CharacteristicProperty.WritableWithoutResponse)
                }
            }.toList()
        }.toMap()
    }

    private val characteristicAdapter: CharacteristicAdapter by lazy {
        CharacteristicAdapter(characteristics) {}
    }

    private var notifyingCharacteristics = mutableListOf<UUID>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ConnectionManager.registerListener(connectionEventListener)
        super.onCreate(savedInstanceState)

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            ?: error("Missing BluetoothDevice from MainActivity!")

        setContentView(R.layout.activity_ble_operations)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
            setLogo(R.mipmap.ic_launcher_foreground)
            setDisplayUseLogoEnabled(true)
            title = getString(R.string.ble_playground)
        }

        ConnectionManager.enableNotifications(device, characteristics.component4())

    }

    override fun onDestroy() {
        ConnectionManager.unregisterListener(connectionEventListener)
        ConnectionManager.teardownConnection(device)
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d("SLEDIM", "onOptionsItemSelected")
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("SetTextI18n")
    private fun neki(sporocilo: String) {
        val textView: TextView = findViewById(R.id.textView)
        val indeks1 = sporocilo.indexOf(":", 0)
        val indeks2 = sporocilo.indexOf(":", indeks1 + 1)
        val indeks3 = sporocilo.indexOf(":", indeks2 + 1)
        val indeks4 = sporocilo.indexOf("}", indeks3 + 1)

        val lineChartTemp: LineChart = findViewById(R.id.graf_temperatura)
        val lineChartVlaga: LineChart = findViewById(R.id.graf_vlaga)

        val trenutniT: Float =
            sporocilo.subSequence(IntRange(indeks1 + 1, indeks2 - 12)).toString().toFloat()
        val trenutniV: Float =
            sporocilo.subSequence(IntRange(indeks2 + 1, indeks3 - 9)).toString().toFloat()

        val tempNabor = num_man(trenutniT, "TemperaturaArr", 10)
        lineChartMake(lineChartTemp, tempNabor, "Temperature")

        val vlagaNabor = num_man(trenutniV, "VlagaArr", 10)
        lineChartMake(lineChartVlaga, vlagaNabor, "Humidity")

        dogodek(sporocilo.subSequence(IntRange(indeks3 + 2, indeks4 - 2)).toString(), textView, textView3)


    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onDisconnect = {
                runOnUiThread {
                    alert {
                        title = "Disconnected"
                        message = "Disconnected from device."
                        positiveButton("OK") { onBackPressed() }
                    }.show()
                }
            }
            onCharacteristicChanged = { _, characteristic ->
                neki(characteristic.value.toString(Charset.defaultCharset()))
            }

            onNotificationsEnabled = { _, characteristic ->
                notifyingCharacteristics.add(characteristic.uuid)
            }

            onNotificationsDisabled = { _, characteristic ->
                notifyingCharacteristics.remove(characteristic.uuid)
            }
        }
    }

    private enum class CharacteristicProperty {
        Readable,
        Writable,
        WritableWithoutResponse,
        Notifiable,
        Indicatable;

    }

    private fun num_man(nova_vrednost: Float, ime: String, velikost: Int): FloatArray {

        if (!fileExists(this, ime)) {
            val vsebina = FloatArray(velikost)
            for (i in 0 until velikost) {
                vsebina[i] = 0.toFloat()
            }

            val fileContents = vsebina.joinToString(postfix = "]")
            this.openFileOutput(ime, Context.MODE_PRIVATE).use {
                it.write(fileContents.toByteArray())
            }

            val nekej = this.openFileInput(ime).bufferedReader().useLines { lines ->
                lines.fold("") { some, text ->
                    "$some\n$text"
                }
            }

            val seznam = nekej.split(", ").toTypedArray()
            val vsebinazavrnit = FloatArray(seznam.size)
            for (i in seznam.indices) {
                vsebinazavrnit[i] = seznam[i].toFloat()
            }
            return vsebinazavrnit
        } else {
            val nekej = this.openFileInput(ime).bufferedReader().useLines { lines ->
                lines.fold("") { some, text ->
                    "$some\n$text"
                }
            }
            val ind1 = nekej.indexOf(",", 0)
            val ind2 = nekej.indexOf("]", ind1)
            val novistring = nekej.subSequence(IntRange(ind1 + 2, ind2 - 1))
                .toString() + ", " + nova_vrednost.toString()


            val seznam = novistring.split(", ").toTypedArray()
            val vsebinazavrnit = FloatArray(seznam.size)
            for (i in seznam.indices) {
                vsebinazavrnit[i] = seznam[i].toFloat()
            }

            val savenew = vsebinazavrnit.joinToString(postfix = "]")
            this.openFileOutput(ime, Context.MODE_PRIVATE).use {
                it.write(savenew.toByteArray())
            }
            return vsebinazavrnit
        }
    }

    private fun fileExists(context: Context, filename: String): Boolean {
        val file = context.getFileStreamPath(filename)
        if (file == null || !file.exists()) {
            return false
        }
        return true
    }

    private fun lineChartMake(lineChart: LineChart, vnos: FloatArray, oznaka: String) {
        val entries = ArrayList<Entry>()

        for (i in 1 until vnos.size) {
            entries.add(Entry(i.toFloat(), vnos[i]))
        }

        val vl = LineDataSet(entries, oznaka)

        vl.mode = LineDataSet.Mode.CUBIC_BEZIER

        vl.setDrawValues(false)
        vl.setDrawFilled(true)
        vl.lineWidth = 3f
        vl.fillColor = R.color.gray
        vl.fillAlpha = R.color.red

        lineChart.xAxis.labelRotationAngle = 0f

        lineChart.data = LineData(vl)

        lineChart.axisRight.isEnabled = false

        lineChart.setTouchEnabled(true)
        lineChart.setPinchZoom(true)

        lineChart.contentDescription
        lineChart.setNoDataText("No data yet!")

        //lineChart.animateX(1800)
        lineChart.invalidate();
    }

    private fun dogodek(vrsta: String, polje: TextView, tekst: TextView) {

        val aBar: ActionBar? = supportActionBar

        if (vrsta == "MATICA") {
            polje.setBackgroundResource(R.drawable.logo_queen)
            //tekst.setBackgroundResource(R.drawable.napis_everything_okay)
            tekst.setBackgroundResource(R.drawable.green_edge)
            tekst.text = "Normal"
        } else if (vrsta == "NIMATICE") {
            polje.setBackgroundResource(R.drawable.no_queen_bee)
            //tekst.setBackgroundResource(R.drawable.napis_no_queen)
            tekst.setBackgroundResource(R.drawable.orange_edge)
            tekst.text = "No queen!"
        } else {
            polje.setBackgroundResource(R.drawable.swarming)
            //tekst.setBackgroundResource(R.drawable.napis_swarming)
            tekst.setBackgroundResource(R.drawable.red_edge)
            tekst.text = "Swarming"
            btnNotify()
        }
    }
    fun btnNotify() {
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationChannel = NotificationChannel(channelId, description, NotificationManager .IMPORTANCE_HIGH)
            notificationChannel.lightColor = Color.BLUE
            notificationChannel.enableVibration(true)
            notificationManager.createNotificationChannel(notificationChannel)

            builder = Notification.Builder(this, channelId).setContentTitle("Warning!")
                .setContentText("Bees swarming!").setSmallIcon(R.mipmap.ic_launcher_round).setLargeIcon(
                    BitmapFactory.decodeResource(this.resources, R.mipmap.ic_launcher_round)).setContentIntent(pendingIntent)
        }
        notificationManager.notify(12345, builder.build())
    }
}