package nhnad.soeun_chat.domain.report.dto;

public record ExcelExportResponse(
        String downloadUrl,
        String fileName
) {}
