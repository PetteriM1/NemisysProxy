package org.itxtech.nemisys.network.encryption;

import lombok.Getter;
import org.itxtech.nemisys.Player;
import org.itxtech.nemisys.scheduler.AsyncTask;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;

public class PrepareEncryptionTask extends AsyncTask {

    private final Player player;
    @Getter
    private String handshakeJwt;
    @Getter
    private SecretKey encryptionKey;
    @Getter
    private Cipher encryptionCipher;
    @Getter
    private Cipher decryptionCipher;

    public PrepareEncryptionTask(Player player) {
        this.player = player;
    }

    @Override
    public void onRun() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp384r1"));
            KeyPair serverKeyPair = generator.generateKeyPair();

            byte[] token = EncryptionUtils.generateRandomToken();
            this.encryptionKey = EncryptionUtils.getSecretKey(serverKeyPair.getPrivate(), EncryptionUtils.generateKey(player.getLoginChainData().getIdentityPublicKey()), token);
            this.handshakeJwt = EncryptionUtils.createHandshakeJwt(serverKeyPair, token).serialize();

            boolean useGcm = player.protocol > 428;
            this.encryptionCipher = EncryptionUtils.createCipher(useGcm, true, this.encryptionKey);
            this.decryptionCipher = EncryptionUtils.createCipher(useGcm, false, this.encryptionKey);
        } catch (Exception ex) {
            player.getServer().getLogger().error("Exception in PrepareEncryptionTask", ex);
        }
    }
}
