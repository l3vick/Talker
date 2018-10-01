package com.pablorojas.talker

import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MotionEvent
import com.firebase.ui.auth.AuthUI
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.sdk25.coroutines.onTouch
import org.jetbrains.anko.startActivity
import org.jetbrains.anko.toast
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import android.support.v4.content.ContextCompat
import com.google.firebase.storage.UploadTask
import org.jetbrains.anko.textColor


class MainActivity : AppCompatActivity() {


    private val LOG_TAG: String = "RECORD AUDIO FILE"

    private val REQUEST_RECORD_AUDIO_PERMISSION: Int = 123

    private var mFileName: String? = null

    private var mRecorder: MediaRecorder? = null

    private var mPlayer: MediaPlayer? = null


    // Requesting permission to RECORD_AUDIO
    private var permissionToRecordAccepted = false
    private var permissions: Array<String> = arrayOf(android.Manifest.permission.RECORD_AUDIO)

    val firebaseStorage = FirebaseStorage.getInstance("gs://talker-dfbd3.appspot.com")

    var storageRef = firebaseStorage.reference

    val user = FirebaseAuth.getInstance().currentUser

    val database = FirebaseDatabase.getInstance()

    var sessionStarted: Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFileName = "${externalCacheDir.absolutePath}/audiorecordtest.3gp"

        mPlayer = MediaPlayer()

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)


        logOut.onClick {
            signOut()
        }

        record.onTouch { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    //playStartMic()
                    resizeBigButton()
                    if (sessionStarted) {
                        startRecording()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    //playStopMic()
                    resizeSmallButton()
                    if (sessionStarted) {
                        stopRecording()
                        pushToFirebase()
                    }
                }
            }
            true
        }

        play.onClick {
            //startPlaying()
        }

        joinSession.onClick {
            listenSession()
            blockSession()
            sessionStarted = true
        }

        createSession.onClick {
            createSession()
            listenSession()
            blockSession()
            sessionStarted = true
        }
    }

    private fun blockSession() {
        joinSession.isEnabled = false
        joinSession.isClickable = false
        joinSession.textColor = ContextCompat.getColor(this, R.color.material_indigo_300)
        createSession.isEnabled = false
        createSession.isClickable = false
        createSession.textColor = ContextCompat.getColor(this, R.color.material_indigo_300)
    }

    private fun playStartMic() {
        val mediaPlayer = MediaPlayer()
        mediaPlayer.setDataSource(assets.openFd("startMic.wav"))
        mediaPlayer.prepare()
        mediaPlayer.start()
        mediaPlayer.setOnCompletionListener {
            mediaPlayer.release()
        }
    }

    private fun playStopMic() {
        val mediaPlayer = MediaPlayer()
        mediaPlayer.setDataSource(assets.openFd("stopMic.wav"))
        mediaPlayer.prepare()
        mediaPlayer.start()
        mediaPlayer.setOnCompletionListener {
            mediaPlayer.release()
        }

    }

    private fun createSession() {
        val tsLong = System.currentTimeMillis() / 1000
        val ts = tsLong.toString()

        val users = HashMap<String, String>()
        users.put("userid", user!!.uid)
        users.put("timestamp", ts)
        users.put("sessionpassword", "")

        val sessionsRef = database.getReference("sessions")
        sessionsRef.child("session_" + meterPicker.value).setValue(users)
    }

    private fun listenSession() {
        val sessionsRef = database.getReference("sessions")
        sessionsRef.child("session_" + meterPicker.value).child("records").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                val value = dataSnapshot.getValue() as HashMap<String, HashMap<String, String>>?

                if (value != null) {
                    for ((key, map) in value) {
                        /*val audioRef = storageRef.child(map.get("audioreference")!!)

                         if (map.get("userid") == user!!.uid) {

                         } else {
                             val ONE_MEGABYTE = (1024 * 1024).toLong()
                             audioRef.getBytes(ONE_MEGABYTE).addOnSuccessListener {
                                 val audioFile = File(mFileName)
                                 val stream = FileOutputStream(audioFile)
                                 stream.write(it)
                                 startPlaying()
                             }.addOnFailureListener {
                                 // Handle any errors
                             }
                         }*/

                        if (map.get("userid") != user!!.uid) {
                            storageRef = firebaseStorage.getReferenceFromUrl(map.get("pathreference")!!)
                            storageRef.getDownloadUrl().addOnSuccessListener(OnSuccessListener<Any> { uri ->
                                val url = uri.toString()
                                startPlaying(url)

                            }).addOnFailureListener(OnFailureListener { e -> Log.i("TAG", e.message) })
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Failed to read value
                Log.w("--------", "Failed to read value.", error.toException())
            }
        })
    }


    private fun resizeBigButton() {
        val params = record.layoutParams
        params.width = (55 * resources.displayMetrics.density).toInt()
        params.height = (55 * resources.displayMetrics.density).toInt()
        record.layoutParams = params
    }

    private fun resizeSmallButton() {
        val params = record.layoutParams
        params.width = (50 * resources.displayMetrics.density).toInt()
        params.height = (50 * resources.displayMetrics.density).toInt()
        record.layoutParams = params
    }

    fun signOut() {
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener {
                    showSnackbar("sign out successful")
                    startActivity<SignInActivity>()
                }

    }

    fun pushToFirebase() {
        val audioFile = File(mFileName)
        val stream = FileInputStream(audioFile)

        val tsLong = System.currentTimeMillis() / 1000
        val ts = tsLong.toString()

        val storageFileName = user!!.uid + "-session" + meterPicker.value + ".3gp"

        val record = HashMap<String, String>()
        record.put("userid", user!!.uid)
        record.put("audioreference", storageFileName)
        val audioRef = storageRef.root.child(storageFileName)
        val uploadTask = audioRef.putStream(stream)

        uploadTask.addOnFailureListener(OnFailureListener {
            toast("Error al enviar audio")
        }).addOnSuccessListener(OnSuccessListener<Any> {
            val metadata = (it as UploadTask.TaskSnapshot).metadata
            record.put("pathreference", metadata!!.reference.toString())
            database.getReference("sessions").child("session_" + meterPicker.value).child("records").child(ts).setValue(record)
        })
    }


    fun showSnackbar(message: String) {
        Snackbar.make(findViewById(android.R.id.content),
                message, Snackbar.LENGTH_SHORT).show()
    }


    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        if (!permissionToRecordAccepted) finish()
    }

    private fun startPlaying(dataSource: String) {
        mPlayer = MediaPlayer().apply {
            try {
                setDataSource(dataSource)
                prepare()
                start()
            } catch (e: IOException) {
                Log.e(LOG_TAG, "prepare() failed")
            }
        }

        mPlayer!!.setOnCompletionListener {
            stopPlaying()
        }
    }

    private fun stopPlaying() {
        mPlayer!!.release()
        mPlayer = null
    }

    private fun startRecording() {
        mRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(mFileName)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

            try {
                prepare()
            } catch (e: IOException) {
                Log.e(LOG_TAG, "prepare() failed")
            }
            start()
        }
    }

    private fun stopRecording() {
        mRecorder?.apply {
            try {
                stop()
            } catch (stopException: RuntimeException) {
                //handle cleanup here
            }
            release()
        }
        mRecorder = null
    }


    override fun onStop() {
        super.onStop()
        mRecorder?.release()
        mRecorder = null
        mPlayer?.release()
        mPlayer = null
    }


}