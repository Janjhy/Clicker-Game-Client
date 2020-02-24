package com.example.clickergame

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.lang.Exception
import java.net.URI


class MainActivity : AppCompatActivity() {
    private lateinit var socket: ClientWebSocket
    private val connectionThread = Thread(ConnectionThread())
    private var points: String? = null
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btn_play.setOnClickListener {
            sendMsg("play")
        }

        btn_reset.setOnClickListener {
            sendMsg("reset")
            btn_reset.isEnabled = false
            btn_play.isEnabled = true
        }
    }

    override fun onStart() {//Handles android onStart lifecycle,
        super.onStart()
        running = true
        connectionThread.start()
    }

    override fun onStop() { //Handles android onStop lifecycle,
        super.onStop()
        sendMsg("exit")
        running = false
        if (socket.connection.isOpen) socket.close()
        setPoints()
    }

    fun parse(message: List<String>) { //Determines what to do with received server message
        when (message[0]) {
            ":won" -> {
                wonPoints(message[1])
            }
            ":next" -> {
                editNext(message[1])
            }
            ":points" -> {
                editPoints(message[1])
            }
            ":nopoints" -> {
                this.runOnUiThread { noPoints() }
            }
            ":id" -> {
                setID(message[1])
            }
            else -> {
            }
        }
    }

    private fun idExists(): Boolean { //Checks if id exists in SharedPreferences
        val sharedPref = this.getPreferences(Context.MODE_PRIVATE)
        return sharedPref.contains(getString(R.string.id_key))
    }

    private fun getID(): String? { //Gets possible saved id from SharedPreferences
        val sharedPref = this.getPreferences(Context.MODE_PRIVATE)
        return sharedPref.getString(getString(R.string.id_key), null)
    }

    private fun setID(text: String) { //Saves user id received from server to SharedPreferences
        if (!idExists()) {
            val sharedPref = this.getPreferences(Context.MODE_PRIVATE) ?: return
            with(sharedPref.edit()) {
                putString(getString(R.string.id_key), text)
                apply()
            }
        } else {
            return
        }
    }

    private fun setPoints() { //If the user has played previously, displays points since last app usage
        if (idExists()) {
            val sharedPref = this.getPreferences(Context.MODE_PRIVATE) ?: return
            with(sharedPref.edit()) {
                putString(getString(R.string.points_key), points)
                apply()
            }
        }
    }

    private fun noPoints() {
        tv_points.text = getString(R.string.no_points)
        btn_reset.isEnabled = true
        btn_play.isEnabled = false
    }

    private fun editNext(text: String) { //Sets the value for next clicks textview
        if (text.toInt() < 0 || text.toInt() > 10) return
        else {
            tv_clicks_display.text = text
        }
    }

    private fun getPoints() { //Gets possible points from SharedPreferences, and displays them
        val sharedPref = this.getPreferences(Context.MODE_PRIVATE)
        val pointsString = sharedPref.getString(getString(R.string.points_key), null)
        if(pointsString != null) {
            tv_points.text = pointsString
        }
        else {
            tv_points.text = getString(R.string.display_points_error)
        }

    }

    private fun editPoints(text: String) {//Sets the value for user points textview
        if (text.toInt() < 0) {
            return
        } else {
            points = text
            tv_points.text = text
        }
    }

    private fun wonPoints(text: String) { //Displays a Snackbar to the user that they won points
        Snackbar.make(
            coordinator,
            "You won $text points!",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun sendMsg(msg: String) { //Function to start message sending
        Thread(OutputMessageThread(msg)).start()
    }

    //A runnable to establish connection to server
    inner class ConnectionThread : Runnable {
        override fun run() {
            try {
                val address = URI("ws://clicker-game-server.herokuapp.com:80/game/")
                socket = ClientWebSocket(address)
                try {
                    socket.connect()

                } catch (e: Exception) {
                    println("Exception: ${e.message}")
                    Snackbar.make(
                        coordinator,
                        "Could not connect to game server.",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                println("Exception: ${e.message}")
                Snackbar.make(
                    coordinator,
                    "Could not find host.",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    //A separate thread to send a message to the server.
    inner class OutputMessageThread(private var message: String) : Runnable {
        override fun run() {
            try {
                socket.send(this.message)
            } catch (e: Exception) {
                println("Exception: ${e.message} 1")
            }
        }
    }

    //Websocket object to handle connection
    inner class ClientWebSocket(serverURI: URI?) :
        WebSocketClient(serverURI) {
        override fun onOpen(handshakedata: ServerHandshake?) {
            if (idExists()) {
                getID()?.let { sendMsg("ID $it") }
                getPoints()
            } else {
                sendMsg("noID")
            }
        }

        override fun onClose(code: Int, reason: String?, remote: Boolean) {
        }


        override fun onMessage(message: String?) { //When websocket receives a message from connection, this is called
            if (message != null) {
                parse(message.split(" "))
            }
        }

        override fun onError(ex: Exception?) {
        }

    }
}

