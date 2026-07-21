package com.example.webrtc

import android.util.Log
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class SignalingClient(
    private val roomId: String,
    private val listener: SignalingListener
) {
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val callRef: DatabaseReference = database.getReference("calls").child(roomId)
    private var isCaller = false

    interface SignalingListener {
        fun onOfferReceived(description: SessionDescription)
        fun onAnswerReceived(description: SessionDescription)
        fun onIceCandidateReceived(candidate: IceCandidate)
    }

    fun initAsCaller() {
        isCaller = true
        
        callRef.child("answer").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val type = snapshot.child("type").getValue(String::class.java)
                    val sdp = snapshot.child("sdp").getValue(String::class.java)
                    if (type != null && sdp != null) {
                        listener.onAnswerReceived(
                            SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
                        )
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("SignalingClient", "Failed to read answer", error.toException())
            }
        })

        callRef.child("candidates").child("callee").addChildEventListener(iceCandidateListener)
    }

    fun initAsCallee() {
        isCaller = false
        
        callRef.child("offer").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val type = snapshot.child("type").getValue(String::class.java)
                    val sdp = snapshot.child("sdp").getValue(String::class.java)
                    if (type != null && sdp != null) {
                        listener.onOfferReceived(
                            SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
                        )
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("SignalingClient", "Failed to read offer", error.toException())
            }
        })

        callRef.child("candidates").child("caller").addChildEventListener(iceCandidateListener)
    }

    fun sendOffer(description: SessionDescription) {
        val data = mapOf(
            "type" to description.type.canonicalForm(),
            "sdp" to description.description
        )
        callRef.child("offer").setValue(data)
    }

    fun sendAnswer(description: SessionDescription) {
        val data = mapOf(
            "type" to description.type.canonicalForm(),
            "sdp" to description.description
        )
        callRef.child("answer").setValue(data)
    }

    fun sendIceCandidate(candidate: IceCandidate) {
        val node = if (isCaller) "caller" else "callee"
        val candidateMap = mapOf(
            "serverUrl" to candidate.serverUrl,
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "sdpCandidate" to candidate.sdp
        )
        callRef.child("candidates").child(node).push().setValue(candidateMap)
    }

    fun destroy() {
    }

    private val iceCandidateListener = object : ChildEventListener {
        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
            val serverUrl = snapshot.child("serverUrl").getValue(String::class.java) ?: ""
            val sdpMid = snapshot.child("sdpMid").getValue(String::class.java) ?: ""
            val sdpMLineIndex = snapshot.child("sdpMLineIndex").getValue(Int::class.java) ?: 0
            val sdpCandidate = snapshot.child("sdpCandidate").getValue(String::class.java) ?: ""

            listener.onIceCandidateReceived(
                IceCandidate(sdpMid, sdpMLineIndex, sdpCandidate)
            )
        }

        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
        override fun onChildRemoved(snapshot: DataSnapshot) {}
        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
        override fun onCancelled(error: DatabaseError) {}
    }
}
