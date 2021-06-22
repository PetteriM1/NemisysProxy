package org.itxtech.nemisys.network.protocol.mcpe;

import org.itxtech.nemisys.network.protocol.mcpe.types.ScoreInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author CreeperFace
 */
public class SetScorePacket extends DataPacket {

    public Action action;
    public List<ScoreInfo> infos;

    public void encode() {
        reset();
        putByte((byte) action.ordinal());

        putUnsignedVarInt(infos.size());
        infos.forEach(it -> {
            putVarLong(it.scoreId);
            putString(it.objective);
            putLInt(it.score);
            if (action == Action.SET) {
                putByte((byte) it.type.ordinal());
                switch(it.type) {
                    case PLAYER:
                    case ENTITY:
                        putEntityUniqueId(it.entityId);
                        break;
                    case FAKE:
                    case INVALID:
                    default:
                        putString(it.name);
                        break;
                }
            }
        });
    }

    @Override
    public void decode() {
        action = Action.values()[getByte()];

        int length = (int) getUnsignedVarInt();
        if (length > 10000) throw new IllegalArgumentException("Score infos too long");
        List<ScoreInfo> infos = new ArrayList<>(length);

        for (int i = 0; i < length; i++) {
            try {
                long id = getVarLong();
                String obj = getString();
                int score = getLInt();
                Type type = Type.values()[getByte()];

                ScoreInfo info = new ScoreInfo(id, obj, score);

                info.type(type);

                if (type == Type.ENTITY || type == Type.PLAYER) {
                    info.entityId = getVarLong();
                } else {
                    info.name = getString();
                }

                infos.add(info);
            } catch (Exception ignore) {}
        }

        this.infos = infos;
    }

    @Override
    public byte pid() {
        return ProtocolInfo.SET_SCORE_PACKET;
    }

    public enum Action {
        SET,
        REMOVE
    }

    public enum Type {
        INVALID,
        PLAYER,
        ENTITY,
        FAKE
    }
}
