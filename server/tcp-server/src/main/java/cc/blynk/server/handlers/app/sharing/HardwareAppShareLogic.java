package cc.blynk.server.handlers.app.sharing;

import cc.blynk.common.model.messages.StringMessage;
import cc.blynk.common.model.messages.protocol.HardwareMessage;
import cc.blynk.common.model.messages.protocol.appllication.sharing.SyncMessage;
import cc.blynk.common.utils.ParseUtil;
import cc.blynk.common.utils.StringUtils;
import cc.blynk.server.dao.SessionDao;
import cc.blynk.server.exceptions.NoActiveDashboardException;
import cc.blynk.server.model.DashBoard;
import cc.blynk.server.model.auth.Session;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static cc.blynk.server.utils.StateHolderUtil.*;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
public class HardwareAppShareLogic {

    private static final Logger log = LogManager.getLogger(HardwareAppShareLogic.class);

    private final SessionDao sessionDao;

    public HardwareAppShareLogic(SessionDao sessionDao) {
        this.sessionDao = sessionDao;
    }

    private static boolean isWriteOperation(String body) {
        return body.length() > 1 && body.charAt(1) == 'w';
    }

    public void messageReceived(ChannelHandlerContext ctx, AppShareStateHolder state, StringMessage message) {
        Session session = sessionDao.userSession.get(state.user);

        DashBoard dashBoard = state.user.profile.getDashById(state.dashId, message.id);
        if (!dashBoard.isActive) {
            throw new NoActiveDashboardException(message.id);
        }

        String[] split = message.body.split(StringUtils.BODY_SEPARATOR_STRING, 2);
        int dashId = ParseUtil.parseInt(split[0], message.id);

        //if dash was shared. check for shared channels
        if (isWriteOperation(split[1])) {
            String sharedToken = state.user.dashShareTokens.get(dashId);
            if (sharedToken != null) {
                for (Channel appChannel : session.appChannels) {
                    if (appChannel != ctx.channel() && needSync(appChannel, sharedToken)) {
                        appChannel.writeAndFlush(new SyncMessage(message.id, message.body));
                    }
                }
            }
        }

        session.sendMessageToHardware(dashId, new HardwareMessage(message.id, split[1]));
    }

}