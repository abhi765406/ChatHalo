sed -i '/var firebaseError/d' app/src/main/java/com/example/MainActivity.kt
sed -i 's/|| firebaseError != null//g' app/src/main/java/com/example/MainActivity.kt

# Replace the LaunchedEffect block with manual init
sed -i '/LaunchedEffect(Unit) {/,/    }/c\
    LaunchedEffect(Unit) {\
        try {\
            com.google.firebase.FirebaseApp.getInstance()\
        } catch (e: Exception) {\
            val options = com.google.firebase.FirebaseOptions.Builder()\
                .setApiKey("AIzaSyAqyqSmrzzK4JdEBFepsQBcod8G-ptg9Oc")\
                .setApplicationId("1:592994181533:web:dc923aeb6a5eed3b974f74")\
                .setDatabaseUrl("https://hologramcall-default-rtdb.firebaseio.com")\
                .setProjectId("hologramcall")\
                .setStorageBucket("hologramcall.firebasestorage.app")\
                .build()\
            com.google.firebase.FirebaseApp.initializeApp(context, options)\
        }\
    }' app/src/main/java/com/example/MainActivity.kt
