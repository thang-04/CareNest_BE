package com.carenest.integration.ai;

import java.util.List;

/** Port gọi embedding API. Chỉ nhận tài liệu tham khảo và query dựng bằng code, không nhận dữ liệu trẻ. */
public interface EmbeddingClient {

    float[] embed(String text);

    /** Trả vector theo đúng thứ tự đầu vào. */
    List<float[]> embedBatch(List<String> texts);

    /** Tên model ghi vào chunk, để biết chunk nào cần reindex khi đổi model. */
    String modelName();
}
