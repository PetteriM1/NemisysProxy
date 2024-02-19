package org.itxtech.nemisys.network.protocol.mcpe;

/**
 * @author MagicDroidX &amp; iNevet
 * Nukkit Project
 */
public interface ProtocolInfo {

    byte LOGIN_PACKET = 0x01;
    byte SERVER_TO_CLIENT_HANDSHAKE_PACKET = 0x03;
    byte CLIENT_TO_SERVER_HANDSHAKE_PACKET = 0x04;
    byte DISCONNECT_PACKET = 0x05;
    byte TEXT_PACKET = 0x09;
    byte ADD_PLAYER_PACKET = 0x0c;
    byte ADD_ENTITY_PACKET = 0x0d;
    byte REMOVE_ENTITY_PACKET = 0x0e;
    byte ADD_ITEM_ENTITY_PACKET = 0x0f;
    byte ADD_PAINTING_PACKET = 0x16;
    byte REMOVE_OBJECTIVE_PACKET = 0x6a;
    byte SET_DISPLAY_OBJECTIVE_PACKET = 0x6b;
    byte SET_SCORE_PACKET = 0x6c;
    byte NETWORK_SETTINGS_PACKET = (byte) 0x8f;
    byte REQUEST_NETWORK_SETTINGS_PACKET = (byte) 0xc1;
    byte BATCH_PACKET = (byte) 0xff;
}
