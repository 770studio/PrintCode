package com.print.code


import android.provider.Settings
import android.util.Log
import com.android.volley.AuthFailureError
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONException
import org.json.JSONObject


class MyFirebaseMessagingService : FirebaseMessagingService() {
    private val serverUrl = "https://770studio.ru/demo/barcode_demo.php"

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    // [START receive_message]
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // [START_EXCLUDE]
        // There are two types of messages data messages and notification messages. Data messages are handled
        // here in onMessageReceived whether the app is in the foreground or background. Data messages are the type
        // traditionally used with GCM. Notification messages are only received here in onMessageReceived when the app
        // is in the foreground. When the app is in the background an automatically generated notification is displayed.
        // When the user taps on the notification they are returned to the app. Messages containing both notification
        // and data payloads are treated as notification messages. The Firebase console always sends notification
        // messages. For more see: https://firebase.google.com/docs/cloud-messaging/concept-options
        // [END_EXCLUDE]

        // TODO(developer): Handle FCM messages here.
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        Log.d(TAG, "From: ${remoteMessage.from}")



        // Also if you intend on generating your own notifications as a result of a received FCM
        // message, here is where that should be initiated. See sendNotification method below.
    }
    // [END receive_message]

    // [START on_new_token]
    /**
     * Called if the FCM registration token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the
     * FCM registration token is initially generated so this is where you would retrieve the token.
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.
        sendRegistrationToServer(token)
    }
    // [END on_new_token]

    /**
     * Schedule async work using WorkManager.
     */
    private fun scheduleJob() {

    }

    /**
     * Handle time allotted to BroadcastReceivers.
     */
    private fun handleNow() {
        Log.d(TAG, "Short lived task is done.")
    }

    /**
     * Persist token to third-party servers.
     *
     * Modify this method to associate the user's FCM registration token with any server-side account
     * maintained by your application.
     *
     * @param token The new token.
     */
    private fun sendRegistrationToServer(token: String?) {
        // TODO: Implement this method to send token to your app server.
        Log.d(TAG, "sendRegistrationTokenToServer($token)")

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)


        val requestQueue = Volley.newRequestQueue(this)
            var jsonParams: JSONObject? = null
            try {

                jsonParams = JSONObject()
                jsonParams.put("token", token )
                jsonParams.put("androidId", androidId)
                jsonParams.put("update_token", true )
            } catch (e: JSONException) {
                e.printStackTrace()

            }

            val jsonObjReq: JsonObjectRequest = object : JsonObjectRequest( Method.POST ,
                    serverUrl, jsonParams,
                    Response.Listener { response ->
                        var jObject: JSONObject? = null
                        try {
                            jObject = JSONObject(response.toString())
                            // val data = jObject.getJSONObject("data")

                        } catch (e: JSONException) {
                            e.printStackTrace()
                         }
                    }, Response.ErrorListener { error -> // sendToLog("VolleyError:", error.getMessage() );
                val networkResponse = error.networkResponse
                if (networkResponse?.data != null) {
                    val jsonError = String(networkResponse.data)
                    // Print Error!
                    // TODO save to localstorage and send later (queue)
                       Log.d(TAG, jsonError)
                }

            }) {
                /**
                 * Passing some request headers
                 */
                @Throws(AuthFailureError::class)
                override fun getHeaders(): Map<String, String> {
                    val headers = HashMap<String, String>()
                    //   headers["Content-Type"] = "application/vnd.api+json"
                    //  headers["Accept"] = "application/vnd.api+json"
                    return headers
                }
            }

            // Adding request to request queue
            requestQueue.add(jsonObjReq)



    }

    /**
     * Create and show a simple notification containing the received FCM message.
     *
     * @param messageBody FCM message body received.
     */
    private fun sendNotification(messageBody: String) {

    }

    companion object {

        private const val TAG = "MyFirebaseMsgService"
    }
}