package datadog.trace.common.writer.ddagent;

import static datadog.trace.core.http.OkHttpUtils.msgpackRequestBodyOf;

import datadog.trace.bootstrap.instrumentation.api.InstrumentationTags;
import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import datadog.trace.core.CoreSpan;
import datadog.trace.core.Metadata;
import datadog.trace.core.MetadataConsumer;
import datadog.trace.core.serialization.GrowableBuffer;
import datadog.trace.core.serialization.Mapper;
import datadog.trace.core.serialization.Writable;
import datadog.trace.core.serialization.WritableFormatter;
import datadog.trace.core.serialization.msgpack.MsgPackWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.RequestBody;

public final class TraceMapperV0_5 implements TraceMapper {

  private final WritableFormatter dictionaryWriter;
  private final DictionaryMapper dictionaryMapper = new DictionaryMapper();
  private final Map<Object, Integer> encoding = new HashMap<>();
  private final GrowableBuffer dictionary;

  private final MetaWriter metaWriter = new MetaWriter();
  private final int size;

  public TraceMapperV0_5() {
    this(2 << 20);
  }

  public TraceMapperV0_5(int dictionarySize, int bufferSize) {
    // growable buffer is implicitly bounded by the fixed size buffer
    // the messages themselves are written into
    this.dictionary = new GrowableBuffer(bufferSize);
    this.dictionaryWriter = new MsgPackWriter(dictionary);
    this.size = bufferSize;
    reset();
  }

  public TraceMapperV0_5(final int dictionarySize) {
    this(dictionarySize, 2 << 20);
  }

  @Override
  public void map(final List<? extends CoreSpan<?>> trace, final Writable writable) {
    writable.startArray(trace.size());
    for (final CoreSpan<?> span : trace) {
      writable.startArray(12);
      /* 1  */
      writeDictionaryEncoded(writable, span.getServiceName());
      /* 2  */
      writeDictionaryEncoded(writable, span.getOperationName());
      /* 3  */
      writeDictionaryEncoded(writable, span.getResourceName());
      /* 4  */
      writable.writeLong(span.getTraceId().toLong());
      /* 5  */
      writable.writeLong(span.getSpanId().toLong());
      /* 6  */
      writable.writeLong(span.getParentId().toLong());
      /* 7  */
      writable.writeLong(span.getStartTime());
      /* 8  */
      writable.writeLong(span.getDurationNano());
      /* 9  */
      writable.writeInt(span.getError());
      /* 10, 11  */
      span.processTagsAndBaggage(metaWriter.withWritable(writable));
      /* 12 */
      writeDictionaryEncoded(writable, span.getType());
    }
  }

  private void writeDictionaryEncoded(final Writable writable, final Object value) {
    final Object target = null == value ? "" : value;
    final Integer encoded = encoding.get(target);
    if (null == encoded) {
      dictionaryWriter.format(target, dictionaryMapper);
      final int dictionaryCode = dictionary.messageCount() - 1;
      encoding.put(target, dictionaryCode);
      // this call can fail, but the dictionary has been written to now
      // so should make sure dictionary state is consistent first
      writable.writeInt(dictionaryCode);
    } else {
      writable.writeInt(encoded);
    }
  }

  @Override
  public Payload newPayload() {
    return new PayloadV0_5(dictionary.slice(), dictionary.messageCount());
  }

  @Override
  public int messageBufferSize() {
    return size; // 2MB
  }

  @Override
  public void reset() {
    dictionary.reset();
    encoding.clear();
  }

  @Override
  public String endpoint() {
    return "v0.5";
  }

  private static class DictionaryMapper implements Mapper<Object> {

    @Override
    public void map(final Object data, final Writable packer) {
      if (data instanceof UTF8BytesString) {
        packer.writeObject(data, null);
      } else {
        packer.writeString(String.valueOf(data), null);
      }
    }
  }

  private static class PayloadV0_5 extends Payload {

    private final ByteBuffer dictionary;
    private final int stringCount;

    private PayloadV0_5(ByteBuffer dictionary, int stringCount) {
      this.dictionary = dictionary;
      this.stringCount = stringCount;
    }

    @Override
    int sizeInBytes() {
      return 1
          + msgpackArrayHeaderSize(stringCount)
          + dictionary.remaining()
          + msgpackArrayHeaderSize(traceCount())
          + body.remaining();
    }

    @Override
    void writeTo(WritableByteChannel channel) throws IOException {
      for (ByteBuffer buffer : toList()) {
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
      }
    }

    @Override
    RequestBody toRequest() {
      return msgpackRequestBodyOf(toList());
    }

    private List<ByteBuffer> toList() {
      return Arrays.asList(
          // msgpack array header with 2 elements (FIXARRAY | 2)
          ByteBuffer.allocate(1).put(0, (byte) 0x92),
          msgpackArrayHeader(stringCount),
          dictionary,
          msgpackArrayHeader(traceCount()),
          body);
    }
  }

  private final class MetaWriter extends MetadataConsumer {

    private Writable writable;

    MetaWriter withWritable(final Writable writable) {
      this.writable = writable;
      return this;
    }

    @Override
    public void accept(Metadata metadata) {
      int metaSize =
          metadata.getBaggage().size()
              + metadata.getTags().size()
              + (null == metadata.getHttpStatusCode() ? 0 : 1)
              + 1;
      int metricsSize =
          (metadata.hasSamplingPriority() ? 1 : 0)
              + (metadata.measured() ? 1 : 0)
              + (metadata.topLevel() ? 1 : 0)
              + 1;
      for (Map.Entry<String, Object> tag : metadata.getTags().entrySet()) {
        if (tag.getValue() instanceof Number) {
          ++metricsSize;
          --metaSize;
        }
      }
      writable.startMap(metaSize);
      // we don't need to deduplicate any overlap between tags and baggage here
      // since they will be accumulated into maps in the same order downstream,
      // we just need to be sure that the size is the same as the number of elements
      for (Map.Entry<String, String> entry : metadata.getBaggage().entrySet()) {
        writeDictionaryEncoded(writable, entry.getKey());
        writeDictionaryEncoded(writable, entry.getValue());
      }
      writeDictionaryEncoded(writable, THREAD_NAME);
      writeDictionaryEncoded(writable, metadata.getThreadName());
      if (null != metadata.getHttpStatusCode()) {
        writeDictionaryEncoded(writable, HTTP_STATUS);
        writeDictionaryEncoded(writable, metadata.getHttpStatusCode());
      }
      for (Map.Entry<String, Object> entry : metadata.getTags().entrySet()) {
        if (!(entry.getValue() instanceof Number)) {
          writeDictionaryEncoded(writable, entry.getKey());
          writeDictionaryEncoded(writable, entry.getValue());
        }
      }
      writable.startMap(metricsSize);
      if (metadata.hasSamplingPriority()) {
        writeDictionaryEncoded(writable, SAMPLING_PRIORITY_KEY);
        writable.writeInt(metadata.samplingPriority());
      }
      if (metadata.measured()) {
        writeDictionaryEncoded(writable, InstrumentationTags.DD_MEASURED);
        writable.writeInt(1);
      }
      if (metadata.topLevel()) {
        writeDictionaryEncoded(writable, InstrumentationTags.DD_TOP_LEVEL);
        writable.writeInt(1);
      }
      writeDictionaryEncoded(writable, THREAD_ID);
      writable.writeLong(metadata.getThreadId());
      for (Map.Entry<String, Object> entry : metadata.getTags().entrySet()) {
        if (entry.getValue() instanceof Number) {
          writeDictionaryEncoded(writable, entry.getKey());
          writable.writeObject(entry.getValue(), null);
        }
      }
    }
  }
}
