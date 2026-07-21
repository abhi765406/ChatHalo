sed -i 's/private val database: FirebaseDatabase? = try {/private val database: FirebaseDatabase = FirebaseDatabase.getInstance()/g' app/src/main/java/com/example/webrtc/SignalingClient.kt
sed -i '/FirebaseDatabase.getInstance()/d' app/src/main/java/com/example/webrtc/SignalingClient.kt
sed -i '/} catch (e: Exception) {/d' app/src/main/java/com/example/webrtc/SignalingClient.kt
sed -i '/null/d' app/src/main/java/com/example/webrtc/SignalingClient.kt
sed -i 's/private val callRef: DatabaseReference? = database?.getReference("calls")?.child(roomId)/private val callRef: DatabaseReference = database.getReference("calls").child(roomId)/g' app/src/main/java/com/example/webrtc/SignalingClient.kt
sed -i 's/callRef?/callRef/g' app/src/main/java/com/example/webrtc/SignalingClient.kt
