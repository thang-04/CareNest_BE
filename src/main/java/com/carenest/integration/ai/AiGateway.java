package com.carenest.integration.ai;

import com.carenest.utils.enums.AiTask;
import java.util.Optional;

/**
 * Port gọi LLM theo task (ADR-0007, AI-06). Domain dựng prompt tối thiểu, gọi {@link #complete}, rồi tự kiểm output;
 * mọi lỗi ném {@link AiClientException} và domain phải có luồng không AI (AI-03).
 */
public interface AiGateway {

    /** Còn ít nhất một provider dùng được cho task (AI bật, đủ base URL, model, key, hợp loại dữ liệu). */
    boolean isAvailable(AiTask task);

    /** Cấu hình task (deadline, số vòng sửa); rỗng khi task chưa cấu hình. */
    Optional<AiProperties.TaskConfig> taskConfig(AiTask task);

    AiRawResult complete(AiRequest request);
}
