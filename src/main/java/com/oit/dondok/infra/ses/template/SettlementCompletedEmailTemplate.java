package com.oit.dondok.infra.ses.template;

public final class SettlementCompletedEmailTemplate {

  private SettlementCompletedEmailTemplate() {}

  public static String subject(String crewTitle) {
    return "[돈독] '%s' 크루 정산이 완료되었습니다".formatted(crewTitle);
  }

  public static String htmlBody(
      String nickname, String crewTitle, Long refundAmount, String deepLink) {
    return """
        <!DOCTYPE html>
        <html lang="ko">
        <head><meta charset="UTF-8"></head>
        <body style="font-family: sans-serif; color: #333; max-width: 600px; margin: 0 auto; padding: 24px;">
          <h2 style="color: #4f46e5;">돈독 정산 완료 안내</h2>
          <p>안녕하세요, <strong>%s</strong>님!</p>
          <p>'<strong>%s</strong>' 크루의 정산이 완료되었습니다.</p>
          <table style="width: 100%%; border-collapse: collapse; margin: 16px 0;">
            <tr>
              <td style="padding: 8px; border: 1px solid #e5e7eb; background: #f9fafb;">환급 금액</td>
              <td style="padding: 8px; border: 1px solid #e5e7eb; font-weight: bold;">%,d원</td>
            </tr>
          </table>
          <p>
            <a href="%s"
               style="display: inline-block; padding: 12px 24px; background: #4f46e5;
                      color: #fff; text-decoration: none; border-radius: 6px;">
              정산 결과 확인하기
            </a>
          </p>
          <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 24px 0;">
          <p style="font-size: 12px; color: #9ca3af;">
            본 메일은 발신 전용입니다. 문의는 앱 내 고객센터를 이용해 주세요.
          </p>
        </body>
        </html>
        """
        .formatted(nickname, crewTitle, refundAmount, deepLink);
  }
}
