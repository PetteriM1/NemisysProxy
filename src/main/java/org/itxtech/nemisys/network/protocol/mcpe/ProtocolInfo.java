package org.itxtech.nemisys.network.protocol.mcpe;

/**
 * @author MagicDroidX &amp; iNevet
 * Nukkit Project
 */
public interface ProtocolInfo {

    int CURRENT_PROTOCOL = 388;

    String MINECRAFT_VERSION_NETWORK = "1.13.0";

    byte LOGIN_PACKET = 0x01;
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
    byte BATCH_PACKET = (byte) 0xff;
}
