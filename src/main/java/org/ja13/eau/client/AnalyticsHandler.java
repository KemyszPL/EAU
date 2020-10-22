package org.ja13.eau.client;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.relauncher.Side;
import org.ja13.eau.EAU;
import org.ja13.eau.i18n.I18N;
import org.ja13.eau.misc.Version;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.ja13.eau.EAU;
import org.ja13.eau.i18n.I18N;
import org.ja13.eau.misc.Version;

import java.io.IOException;

/**
 * Sent analytics information about the mod and the game configuration.<br>
 * Singleton class. Uses the {@link cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent} and must be registered by
 * the caller on the {@link cpw.mods.fml.common.FMLCommonHandler} bus.
 *
 * @author metc
 */
public class AnalyticsHandler {

    private final static String URL = "http://mc.electrical-age.net/version.php?id=%s&v=%s&l=%s";

    private static AnalyticsHandler instance;

    private boolean ready = false;

    public static AnalyticsHandler getInstance() {
        if (instance == null)
            instance = new AnalyticsHandler();
        return instance;
    }

    private AnalyticsHandler() {
        Thread thread = new Thread(() -> {
            try {
                // build URL

                String version = Version.getVersionName().replaceAll("\\s+", "");
                String lang = I18N.getCurrentLanguage();
                /*

                Things I am gathering (and why):

                version: to be able to know what versions of the mod are being used
                lang: to know what language packs I should be having people focus on (perhaps advertise they can add language translations by contacting us)
                playerUUID: to be able to tell different analytics requests apart
                cableFactor: to know how many people are using this feature (I plan to remove it)
                cableResistanceMultiplier: to know if people are experimenting or using a different value than default (better stability with higher values?)
                explosionEnable: to know if explosions are something people are disabling because of a nuisance (I plan to redo them a bit)
                replicatorPop: how many people (like me) don't care for replicators (perhaps we can make a more fun passive mob!) *Electropuppy!*

                As I see fit, I may add more things in newer releases to see what people are doing with the mod, and what direction to go in.

                 */

                String url = "";

                if (EAU.analyticsPlayerUUIDOptIn && FMLCommonHandler.instance().getEffectiveSide()== Side.CLIENT) {
                    // PLAYER HAS OPTED INTO SENDING THEIR UUID (and is not a server)
                    String formatUrl = "%s?version=%s&lang=%s&uuid=%s&name=%s";
                    url = String.format(formatUrl, EAU.analyticsURL, version, lang, Minecraft.getMinecraft().getSession().func_148256_e().getId().toString(), Minecraft.getMinecraft().getSession().getPlayerID());
                } else {
                    // PLAYER HAS NOT OPTED INTO SENDING THEIR UUID
                    String formatUrl = "%s?version=%s&lang=%s&uuid=%s";
                    url = String.format(formatUrl, EAU.analyticsURL, version, lang, EAU.playerUUID);
                }

                // query the server now.
                CloseableHttpClient client = HttpClientBuilder.create().build();
                CloseableHttpResponse response = client.execute(new HttpGet(url));
                // check response code.
                int repCode = response.getStatusLine().getStatusCode();
                if (repCode != HttpStatus.SC_OK) {
                    throw new IOException("HTTP error " + repCode);
                }
                // cleanup
                response.close();
                client.close();
            } catch (Exception e) {
                String error = "Unable to send analytics data to " + EAU.analyticsURL + ": " + e.getMessage() + ".";
                System.err.println(error);
            }
        });

        thread.start();
    }

    @SubscribeEvent
    public void tick(ClientTickEvent event) {
        if (!ready || event.phase == Phase.START)
            return;

        final Minecraft m = FMLClientHandler.instance().getClient();
        final WorldClient world = m.theWorld;

        if (world == null)
            return;

        if (!ready)
            return;

        FMLCommonHandler.instance().bus().unregister(this);
        ready = false;
    }
}
