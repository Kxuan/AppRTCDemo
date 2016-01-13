/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.appspot.apprtc;

import org.appspot.apprtc.RoomParametersFetcher.RoomParametersFetcherEvents;
import org.appspot.apprtc.WebSocketChannelClient.WebSocketChannelEvents;
import org.appspot.apprtc.WebSocketChannelClient.WebSocketConnectionState;
import org.appspot.apprtc.util.LooperExecutor;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

/**
 * Negotiates signaling for chatting with apprtc.appspot.com "rooms".
 * Uses the client<->server specifics of the apprtc AppEngine webapp.
 * <p/>
 * <p>To use: create an instance of this object (registering a message handler) and
 * call connectToRoom().  Once room connection is established
 * onConnectedToRoom() callback with room parameters is invoked.
 * Messages to other party (with local Ice candidates and answer SDP) can
 * be sent after WebSocket connection is established.
 */
public class WebSocketRTCClient implements AppRTCClient,
        WebSocketChannelEvents {
    private static final String TAG = "WSRTCClient";
    private static final String ROOM_JOIN = "join";
    private static final String ROOM_MESSAGE = "message";
    private static final String ROOM_LEAVE = "leave";
    private long localClientId;

    private enum ConnectionState {
        NEW, CONNECTED, CLOSED, ERROR
    }

    ;

    private enum MessageType {
        MESSAGE, LEAVE
    }

    ;
    private final LooperExecutor executor;
    private SignalingEvents events;
    private WebSocketChannelClient wsClient;
    private ConnectionState roomState;
    private RoomConnectionParameters connectionParameters;

    public WebSocketRTCClient(SignalingEvents events, LooperExecutor executor) {
        this.events = events;
        this.executor = executor;
        roomState = ConnectionState.NEW;
        executor.requestStart();
    }


    @Override
    public void requestRoomInfo() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "cmd", "room");
                wsClient.send(json.toString());
                Log.d(TAG, "signal connected");
            }
        });

    }

    // --------------------------------------------------------------------
    // AppRTCClient interface implementation.
    // Asynchronously connect to an AppRTC room URL using supplied connection
    // parameters, retrieves room parameters and connect to WebSocket server.
    @Override
    public void connectToRoom(RoomConnectionParameters connectionParameters) {
        this.connectionParameters = connectionParameters;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                connectToRoomInternal();
            }
        });
    }

    @Override
    public void disconnectFromRoom() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                disconnectFromRoomInternal();
            }
        });
        executor.requestStop();
    }

    // Connects to room - function runs on a local looper thread.
    //
    private void connectToRoomInternal() {
        String connectionUrl = getConnectionUrl(connectionParameters);//构造房间信息,全地址
        Log.d(TAG, "Connect to room: " + connectionUrl);
        roomState = ConnectionState.NEW;
        wsClient = new WebSocketChannelClient(executor, this);

        RoomParametersFetcherEvents callbacks = new RoomParametersFetcherEvents() {
            @Override
            public void onSignalingParametersReady(
                    final SignalingParameters params) {
                WebSocketRTCClient.this.executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        WebSocketRTCClient.this.signalingParametersReady(params);
                    }
                });
            }

            @Override
            public void onSignalingParametersError(String description) {
                WebSocketRTCClient.this.reportError(description);
            }
        };

        new RoomParametersFetcher(connectionUrl, null, callbacks).makeRequest();
    }

    // Disconnect from room and send bye messages - runs on a local looper thread.
    private void disconnectFromRoomInternal() {

        JSONObject json = new JSONObject();
        jsonPut(json, "cmd", "leave");
        wsClient.send(json.toString());

        Log.d(TAG, "Disconnect. Room state: " + roomState);
        if (roomState == ConnectionState.CONNECTED) {
            Log.d(TAG, "Closing room.");
        }
        roomState = ConnectionState.CLOSED;
        if (wsClient != null) {
            wsClient.disconnect(true);
        }
    }

    // Helper functions to get connection, post message and leave message URLs
    //构造消息
    private String getConnectionUrl(
            RoomConnectionParameters connectionParameters) {
        return connectionParameters.roomUrl + "/" + ROOM_JOIN + "/"
                + connectionParameters.roomId;
    }

    // Callback issued when room parameters are extracted. Runs on local
    // looper thread.
    private void signalingParametersReady(
            final SignalingParameters signalingParameters) {
        Log.d(TAG, "Room connection completed.");
        roomState = ConnectionState.CONNECTED;

        //本机的客户端ID
        localClientId = signalingParameters.clientId;

        // Connect and register WebSocket client.
        wsClient.connect(signalingParameters.wssUrl, signalingParameters.wssPostUrl);
        wsClient.register(connectionParameters.roomId, signalingParameters.clientId);


        // Fire connection and signaling parameters events.
        events.onConnectedToRoom(signalingParameters);
    }

    // Send local offer SDP to the other participant.
    @Override
    public void sendOfferSdp(final long peerId, final SessionDescription sdp, final boolean isHelper) {

        Log.w(TAG, "Calling sendOfferSdp");

        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending offer SDP in non connected state.");
                    return;
                }

                JSONObject json = new JSONObject();
                jsonPut(json, "cmd", "offer");
                jsonPut(json, "to", peerId);
                jsonPut(json, "isHelper", isHelper);
                JSONObject jsonContent = new JSONObject();
                jsonPut(jsonContent, "sdp", sdp.description);
                jsonPut(jsonContent, "type", "offer");
                jsonPut(json, "content", jsonContent);
                wsClient.send(json.toString());
            }
        });
    }

    // Send local answer SDP to the other participant.

    /**
     * 发送 answer 消息（应答offer消息）
     * <p/>
     * 如果sdp为null则拒绝offer，否则接受offer
     *
     * @param peerId 对端Id
     * @param sdp    本地的sdp。
     */
    @Override
    public void sendAnswerSdp(final long peerId, final SessionDescription sdp) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "cmd", "answer");
                jsonPut(json, "to", peerId);
                if (sdp != null) {
                    jsonPut(json, "accept", true);
                    JSONObject jsonContent = new JSONObject();
                    jsonPut(jsonContent, "sdp", sdp.description);
                    jsonPut(jsonContent, "type", "answer");
                    jsonPut(json, "content", jsonContent);
                } else {
                    jsonPut(json, "accept", false);
                }
                wsClient.send(json.toString());
            }
        });
    }

    // Send Ice candidate to the other participant.
    @Override
    public void sendLocalIceCandidate(final long peerId, final IceCandidate candidate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                JSONObject jsonContent = new JSONObject();
                jsonPut(json, "cmd", "candidate");
                jsonPut(json, "to", peerId);
                jsonPut(jsonContent, "label", candidate.sdpMLineIndex);
                jsonPut(jsonContent, "id", candidate.sdpMid);
                jsonPut(jsonContent, "candidate", candidate.sdp);
                jsonPut(json, "content", jsonContent);
                // Call receiver sends ice candidates to websocket server.
                wsClient.send(json.toString());
            }
        });
    }

    // --------------------------------------------------------------------
    // WebSocketChannelEvents interface implementation.
    // All events are called by WebSocketChannelClient on a local looper thread
    // (passed to WebSocket client constructor).
    @Override
    public void onWebSocketMessage(final String msg) {
        if (wsClient.getState() != WebSocketConnectionState.REGISTERED) {
            Log.e(TAG, "Got WebSocket message in non registered state.");
            return;
        }
        try {
            JSONObject json = new JSONObject(msg);
            JSONObject jsonContent;
            String type = json.optString("type");
            switch (type) {
                case "offer": {
                    long fromPeerId = json.getLong("from");
                    jsonContent = json.getJSONObject("content");
                    //收到offer消息。创建sdp并通知主程序
                    SessionDescription sdp = new SessionDescription(
                            SessionDescription.Type.OFFER,
                            jsonContent.getString("sdp"));
                    events.onRemoteOffer(fromPeerId, sdp);
                    break;
                }
                case "answer": {
                    long fromPeerId = json.getLong("from");
                    //收到answer消息
                    // 如果对方接受则传递sdp，否则sdp参数传null
                    if (json.getBoolean("accept")) {
                        jsonContent = json.getJSONObject("content");
                        SessionDescription sdp = new SessionDescription(
                                SessionDescription.Type.ANSWER,
                                jsonContent.getString("sdp"));
                        events.onRemoteAnswer(fromPeerId, sdp);
                    } else {
                        events.onRemoteAnswer(fromPeerId, null);
                    }
                    break;
                }
                case "candidate": {
                    long fromPeerId = json.getLong("from");
                    jsonContent = json.getJSONObject("content");
                    IceCandidate candidate = new IceCandidate(
                            jsonContent.getString("id"),
                            jsonContent.getInt("label"),
                            jsonContent.getString("candidate"));
                    events.onRemoteIceCandidate(fromPeerId, candidate);
                    break;
                }

                case "leave": {
                    long leaveId = json.getLong("id");
                    Log.d(TAG, "leaveId:" + leaveId);
                    events.onRemoteLeave(leaveId);
                    break;

                }
                case "join": {
                    long fromPeerId = json.getLong("id");
                    events.onClientJoin(fromPeerId, json.getString("device"));
                    break;
                }
                case "room": {
                    JSONArray jsonArray;
                    jsonArray = json.getJSONArray("clients");
                    if (jsonArray.length() == 1) {
                        //房间只有自己，先return
                        return;
                    }
                    //拿到所有的客户id和设备类型,加载到选择列表供用户选择
                    ClientInfo[] clientIdString = new ClientInfo[jsonArray.length() - 1];
                    int j = 0;
                    long clientId = 0;
                    String device = null;
                    for (int i = 0; i < jsonArray.length(); i++) {
//                        long clientId = 0;
//                        String device = null;
                        JSONObject jsonClient = (JSONObject) jsonArray.get(i);
                        clientId = jsonClient.getLong("id");
                        device = jsonClient.getString("device");
                        //显示的是本机设备,localClientId,本机设备的Id，client服务器返回的id
                        if (clientId != localClientId) {
                            clientIdString[j++] = new ClientInfo(clientId, device);
                        }
                    }
                    //只有2个客户端，就直接建立连接
                    if (jsonArray.length() == 2) {
                        events.connect(clientIdString[0].getClientId());
                    } else {
                        events.selectClientItem(clientIdString);
                    }
                    break;
                }
                default:
                    Log.w(TAG, "Unexpected WebSocket message :" + msg);
            }

        } catch (JSONException e) {
            reportError("WebSocket message JSON parsing error: " + e.toString());
        }
    }

    @Override
    public void onWebSocketClose() {
        events.onChannelClose();
    }

    @Override
    public void onWebSocketError(String description) {
        reportError("WebSocket error: " + description);
    }

    // --------------------------------------------------------------------
    // Helper functions.
    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.ERROR) {
                    roomState = ConnectionState.ERROR;
                    events.onChannelError(errorMessage);
                }
            }
        });
    }

    // Put a |key|->|value| mapping in |json|.
    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
