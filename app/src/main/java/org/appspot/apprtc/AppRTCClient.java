/*
 *  Copyright 2013 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.appspot.apprtc;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import java.util.List;

/**
 * AppRTCClient is the interface representing an AppRTC client.
 */
public interface AppRTCClient {

    /**
     * Struct holding the connection parameters of an AppRTC room.
     */
    public static class RoomConnectionParameters {
        public final String roomUrl;
        public final long roomId;

        public RoomConnectionParameters(
                String roomUrl, long roomId) {
            this.roomUrl = roomUrl;
            this.roomId = roomId;
        }
    }
    public void requestRoomInfo();
    /**
     * Asynchronously connect to an AppRTC room URL using supplied connection
     * parameters. Once connection is established onConnectedToRoom()
     * callback with room parameters is invoked.
     */
    public void connectToRoom(RoomConnectionParameters connectionParameters);

    /**
     * Send offer SDP to the other participant.
     */
    public void sendOfferSdp(final long clientId, final SessionDescription sdp, boolean isHelper);

    /**
     * Send answer SDP to the other participant.
     */
    public void sendAnswerSdp(final long peerId, final SessionDescription sdp);

    /**
     * Send Ice candidate to the other participant.
     */
    public void sendLocalIceCandidate(long peerId, final IceCandidate candidate);

    /**
     * Disconnect from room.
     */
    public void disconnectFromRoom();

    /**
     * Struct holding the signaling parameters of an AppRTC room.
     */
    public static class SignalingParameters {
        public final List<PeerConnection.IceServer> iceServers;
        public final long clientId;
        public final String wssUrl;
        public final String wssPostUrl;
        public final SessionDescription offerSdp;
        public final List<IceCandidate> iceCandidates;

        public SignalingParameters(
                List<PeerConnection.IceServer> iceServers,
                long clientId,
                String wssUrl, String wssPostUrl,
                SessionDescription offerSdp, List<IceCandidate> iceCandidates) {
            this.iceServers = iceServers;
            this.clientId = clientId;
            this.wssUrl = wssUrl;
            this.wssPostUrl = wssPostUrl;
            this.offerSdp = offerSdp;
            this.iceCandidates = iceCandidates;
        }
    }

    /**
     * Callback interface for messages delivered on signaling channel.
     * <p/>
     * <p>Methods are guaranteed to be invoked on the UI thread of |activity|.
     */
    public static interface SignalingEvents {
        /**
         * Callback fired once the room's signaling parameters
         * SignalingParameters are extracted.
         */
        public void onConnectedToRoom(final SignalingParameters params);

        /**
         * Callback fired once remote SDP is received.
         */
        void onRemoteOffer(long peerId, final SessionDescription sdp);

        /**
         * 对端应答Offer请求
         *
         * @param peerId 对端ID
         * @param sdp    对端sdp，对端拒绝时sdp为null
         */
        void onRemoteAnswer(long peerId, final SessionDescription sdp);

        void onClientJoin(long peerId, String deviceType);



        /**
         * Callback fired once remote Ice candidate is received.
         */
        public void onRemoteIceCandidate(long peerId, final IceCandidate candidate);


        public void onRemoteLeave(long remoteLeaveId);

        /**
         * Callback fired once channel is closed.
         */
        public void onChannelClose();

        /**
         * Callback fired once channel error happened.
         */
        public void onChannelError(final String description);

        //显示客户端列表
        void selectClientItem(ClientInfo[] clientIdString);


        void connect(long masterId);
    }
}
