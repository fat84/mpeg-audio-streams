/**
 * Class:    MPEGAudio<br/>
 * <br/>
 * Created:  25.10.2017<br/>
 * Filename: MPEGAudio.java<br/>
 * Version:  $Revision$<br/>
 * <br/>
 * last modified on $Date$<br/>
 *               by $Author$<br/>
 * <br/>
 * @author <a href="mailto:sebastian.weiss@nacamar.de">Sebastian A. Weiss, nacamar GmbH</a>
 * @version $Author$ -- $Revision$ -- $Date$
 * <br/>
 * (c) Sebastian A. Weiss, nacamar GmbH 2017 - All rights reserved.
 */
package net.addradio.codec.mpeg.audio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.addradio.codec.id3.model.ID3Tag;
import net.addradio.codec.mpeg.audio.codecs.MPEGAudioCodecException;
import net.addradio.codec.mpeg.audio.model.MPEGAudioContent;
import net.addradio.codec.mpeg.audio.model.MPEGAudioEncodingFormat;
import net.addradio.codec.mpeg.audio.model.MPEGAudioFrame;
import net.addradio.codec.mpeg.audio.tools.MPEGAudioContentCollectorHandler;
import net.addradio.codec.mpeg.audio.tools.MPEGAudioContentFilter;
import net.addradio.codec.mpeg.audio.tools.MPEGAudioContentHandler;
import net.addradio.codec.mpeg.audio.tools.MPEGAudioFirstContentOnlyHandler;

/**
 * MPEGAudio.
 */
public class MPEGAudio {

    /** {@link Logger} LOG. */
    private final static Logger LOG = LoggerFactory.getLogger(MPEGAudio.class);

    /**
     * createEncodingFormatForFrame.
     * @param frame {@link MPEGAudioFrame}
     * @return {@link MPEGAudioEncodingFormat} corresponding to frame.
     */
    public static MPEGAudioEncodingFormat createEncodingFormatForFrame(final MPEGAudioFrame frame) {
        if (frame == null) {
            throw new NullPointerException("frame MUST NOT be null!"); //$NON-NLS-1$
        }
        final MPEGAudioEncodingFormat mef = new MPEGAudioEncodingFormat();
        mef.setBitRate(frame.getBitRate());
        mef.setCopyright(frame.isCopyright());
        mef.setEmphasis(frame.getEmphasis());
        mef.setErrorProtected(frame.isErrorProtected());
        mef.setLayer(frame.getLayer());
        mef.setMode(frame.getMode());
        mef.setModeExtension(frame.getModeExtension());
        mef.setOriginal(frame.isOriginal());
        mef.setPrivate(frame.isPrivate());
        mef.setSamplingRate(frame.getSamplingRate());
        mef.setVersion(frame.getVersion());
        return mef;
    }

    /**
     * decode.
     * @param file {@link File}
     * @return {@link DecodingResult} or {@code null} if file could not be opened.
     */
    public static final DecodingResult decode(final File file) {
        return decode(file, MPEGAudioContentFilter.ACCEPT_ALL);
    }

    /**
     * decode.
     * @param file {@link File}
     * @param filter {@link MPEGAudioContentFilter}
     * @return {@link DecodingResult} or {@code null} if file could not be opened.
     */
    public static DecodingResult decode(final File file, final MPEGAudioContentFilter filter) {
        try (final InputStream is = new FileInputStream(file)) {
            return decode(is, filter);
        } catch (final IOException e1) {
            MPEGAudio.LOG.error(e1.getLocalizedMessage(), e1);
        }
        return null;
    }

    /**
     * decode.
     * @param is {@link InputStream}
     * @return {@link DecodingResult}
     */
    public static final DecodingResult decode(final InputStream is) {
        return decode(is, MPEGAudioContentFilter.ACCEPT_ALL);
    }

    /**
     * decode.
     * @param is {@link InputStream}
     * @param filter {@link MPEGAudioContentFilter}
     * @return {@link DecodingResult}
     */
    public static DecodingResult decode(final InputStream is, final MPEGAudioContentFilter filter) {
        final MPEGAudioContentCollectorHandler handler = new MPEGAudioContentCollectorHandler();
        final long sb = decode(is, filter, handler);
        // SEBASTIAN what about decoding processes that went wrong? Introduce a status return value!
        return new DecodingResultOverContentCollector(sb, handler);
    }

    /**
     * decode.
     * @param is {@link InputStream}
     * @param filter {@link MPEGAudioContentFilter}
     * @param handler {@link MPEGAudioContentHandler}
     * @return {@code long} number of skipped bits or {@code -1} in case of occurring IOException;
     */
    public static long decode(final InputStream is, final MPEGAudioContentFilter filter,
            final MPEGAudioContentHandler handler) {
        try (final MPEGAudioFrameInputStream mafis = new MPEGAudioFrameInputStream(is)) {
            MPEGAudioContent frame = null;
            //            int count = 0;
            //            long bytesSoFar = 0;
            while ((frame = mafis.readFrame()) != null) {
                //                final int frameLength = ((MPEGAudioFrame) frame).getFrameLength();
                //                bytesSoFar += frameLength;
                //                System.out.println("counter " + count++ + ", frame length: " + frameLength + ", bytesSoFar: 0x"
                //                        + Long.toHexString(bytesSoFar).toUpperCase() + ", frame: " + frame);
                //                HexDump.printHexDumpString(((MPEGAudioFrame) frame).getPayload());
                //                System.out.println("MDB: " + ((MPEGAudioFrame) frame).getMainDataBegin());
                if (filter.accept(frame)) {
                    if (handler.handle(frame)) {
                        break;
                    }
                }
            }
            return mafis.getSkippedBits();
        } catch (final IOException e) {
            MPEGAudio.LOG.error(e.getLocalizedMessage(), e);
        }
        return -1;
    }

    /**
     * decode.
     * @param fileName {@link String}
     * @return {@link DecodingResult} or {@code null} if file could not be opened.
     */
    public static final DecodingResult decode(final String fileName) {
        return decode(fileName != null ? new File(fileName) : null);
    }

    /**
     * decode.
     * @param fileName {@link String}
     * @param filter {@link MPEGAudioContentFilter}
     * @return {@link DecodingResult} or {@code null} if file could not be opened.
     */
    public static final DecodingResult decode(final String fileName, final MPEGAudioContentFilter filter) {
        return decode(fileName != null ? new File(fileName) : null, filter);
    }

    /**
     * decodeFirstID3Tag.
     * @param file {@link File}
     * @return {@link ID3Tag} first ID3Tag found or {@code null} if file could not be opened.
     */
    public static ID3Tag decodeFirstID3Tag(final File file) {
        if (file == null) {
            throw new NullPointerException("file MUST NOT be null!"); //$NON-NLS-1$
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            return decodeFirstID3Tag(fis);
        } catch (final IOException e) {
            MPEGAudio.LOG.error(e.getLocalizedMessage(), e);
            return null;
        }
    }

    /**
     * decodeFirstID3Tag.
     * @param is {@link InputStream}
     * @return {@link ID3Tag}
     */
    public static ID3Tag decodeFirstID3Tag(final InputStream is) {
        return (ID3Tag) decodeFirstMPEGAudioContent(is, MPEGAudioContentFilter.ID3_TAGS);
    }

    /**
     * decodeFirstMPEGAudioContent.
     * @param is {@link InputStream}
     * @param filter {@link MPEGAudioContentFilter}
     * @return {@link MPEGAudioContent} first content decoded from stream matching the filter's criteria or {@code null} if no adequate content will be found.
     */
    public static MPEGAudioContent decodeFirstMPEGAudioContent(final InputStream is,
            final MPEGAudioContentFilter filter) {
        final MPEGAudioFirstContentOnlyHandler handler = new MPEGAudioFirstContentOnlyHandler();
        decode(is, filter, handler);
        return handler.getFirstContent();
    }

    /**
     * decodeFirstMPEGAudioFrame.
     * @param buffer {@code byte[]}
     * @return {@link MPEGAudioFrame} or {@code null} if nothing could be decoded.
     */
    public static MPEGAudioFrame decodeFirstMPEGAudioFrame(final byte[] buffer) {
        return decodeFirstMPEGAudioFrame(new ByteArrayInputStream(buffer));
    }

    /**
     * decodeFirstMPEGAudioFrame.
     * @param is {@link InputStream}
     * @return {@link MPEGAudioFrame}
     */
    public static MPEGAudioFrame decodeFirstMPEGAudioFrame(final InputStream is) {
        return (MPEGAudioFrame) decodeFirstMPEGAudioContent(is, MPEGAudioContentFilter.MPEG_AUDIO_FRAMES);
    }

    /**
     * encode.
     * @param frames {@link List}{@code <}{@link MPEGAudioContent}{@code >}
     * @return {@code byte[]} or  {@code null} if an error occurred.
     */
    public static byte[] encode(final List<? extends MPEGAudioContent> frames) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            encode(frames, baos, true);
            return baos.toByteArray();
        } catch (final IOException e) {
            MPEGAudio.LOG.error(e.getLocalizedMessage(), e);
        }
        return null;
    }

    /**
     * encode.
     * @param frames {@link List}{@code <}{@link MPEGAudioContent}{@code >}
     * @param os {@link OutputStream}
     * @param autoClose {@code boolean} if {@code true} os will be closed after write.
     * @throws IOException due to IO problems.
     */
    public static void encode(final List<? extends MPEGAudioContent> frames, final OutputStream os,
            final boolean autoClose) throws IOException {
        @SuppressWarnings("resource")
        MPEGAudioFrameOutputStream mafos = null;
        try {
            mafos = new MPEGAudioFrameOutputStream(os);
            for (final MPEGAudioContent mpegAudioFrame : frames) {
                try {
                    mafos.writeFrame(mpegAudioFrame);
                    mafos.flush();
                } catch (final MPEGAudioCodecException e) {
                    MPEGAudio.LOG.error(e.getLocalizedMessage(), e);
                }
            }
        } finally {
            if (autoClose && (mafos != null)) {
                try {
                    mafos.close();
                } catch (final IOException e) {
                }
            }
        }
    }

}
