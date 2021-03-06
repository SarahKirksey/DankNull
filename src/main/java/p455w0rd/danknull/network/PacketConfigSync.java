package p455w0rd.danknull.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import p455w0rd.danknull.init.ModConfig;
import p455w0rd.danknull.init.ModConfig.Options;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author p455w0rd
 */
public class PacketConfigSync implements IMessage {

    public transient Map<String, Object> values;

    public PacketConfigSync() {
    }

    public PacketConfigSync(final Map<String, Object> valuesIn) {
        values = valuesIn;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void fromBytes(final ByteBuf buf) {
        final short len = buf.readShort();
        final byte[] compressedBody = new byte[len];

        for (short i = 0; i < len; i++) {
            compressedBody[i] = buf.readByte();
        }

        try {
            final ObjectInputStream obj = new ObjectInputStream(new GZIPInputStream(new ByteArrayInputStream(compressedBody)));
            values = (Map<String, Object>) obj.readObject();
            obj.close();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        final ByteArrayOutputStream obj = new ByteArrayOutputStream();

        try {
            final GZIPOutputStream gzip = new GZIPOutputStream(obj);
            final ObjectOutputStream objStream = new ObjectOutputStream(gzip);
            objStream.writeObject(values);
            objStream.close();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        buf.writeShort(obj.size());
        buf.writeBytes(obj.toByteArray());
    }

    public static class Handler implements IMessageHandler<PacketConfigSync, IMessage> {
        @Override
        public IMessage onMessage(final PacketConfigSync message, final MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> handle(message, ctx));
            return null;
        }

        private void handle(final PacketConfigSync message, final MessageContext ctx) {
            if (ctx.getClientHandler() != null) {
                Options.creativeBlacklist = (String) message.values.getOrDefault(ModConfig.NAME_CREATIVE_BLACKLIST, "");
                Options.creativeWhitelist = (String) message.values.getOrDefault(ModConfig.NAME_CREATIVE_WHITELIST, "");
                Options.oreBlacklist = (String) message.values.getOrDefault(ModConfig.NAME_OREDICT_BLACKLIST, "");
                Options.oreWhitelist = (String) message.values.getOrDefault(ModConfig.NAME_OREDICT_WHITELIST, "");
                Options.disableOreDictMode = (Boolean) message.values.getOrDefault(ModConfig.NAME_DISABLE_OREDICT, false);
            }
        }
    }
}
