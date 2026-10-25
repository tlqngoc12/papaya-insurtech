# Design Decisions — AI Challenge 10: Fraud Detection Scoring Engine

> Tài liệu này dùng để review và chốt các quyết định thiết kế **trước khi viết code**.
> Stack: Java 21, Maven, JUnit 5, Jackson.
> Nguồn duy nhất: `docs/AI_Challenge_10.md`.

## Cách dùng

- Mỗi mục có mã (`D`, `R`, `S`, `M`, `O`, `T`) để tiện nhắc lại khi review và khi viết code/test.
- **Đề xuất của Claude** chỉ là gợi ý. Phần **Quyết định** và cột **Trọng số** là quyết định của tác giả.
- **Trạng thái:** đã chốt ngày 2026-10-25. Khi phụ lục (Q1–Q24, G1–G14) khác với phần thân thì phụ lục được ưu tiên. Nguồn gốc của tài liệu: xem README, mục 10.
- Các mục gắn ⚠️ là chỗ đề **tự mâu thuẫn** hoặc ảnh hưởng lớn đến metric. Nên chốt các mục này trước.

## Tóm tắt các điểm nóng

| Mã | Điểm nóng | Vì sao quan trọng |
|----|-----------|-------------------|
| D10 ⚠️ | "25 phantom claims" trong khi rule cần "> 30 claims/ngày/provider" | Không thể vừa đúng số lượng vừa kích hoạt rule mà không sinh FP hoặc lệch số đếm |
| D11 ⚠️ | "Provider never operate on weekends" và rule "< 5% weekend volume"; ~40 claims/provider | Chính các claim gian lận cuối tuần có thể đẩy tỉ lệ cuối tuần lên ≥ 5%, khiến rule tự vô hiệu |
| R3/R4 ⚠️ | Upcoding tính "mean theo procedure code" nhưng một claim có nhiều procedure và chỉ một `submitted_amount` | Không rõ gán số tiền cho procedure nào. Outlier còn tự làm phình độ lệch chuẩn (masking) |
| S1 ⚠️ | "Weighted sum, normalized to 0–100" không nói chuẩn hoá theo gì | Quyết định thang điểm, ngưỡng phân loại và việc trọng số có tác động đến metric hay không |
| M1 ⚠️ | "False positive rate" có thể là FP/(FP+TN) hoặc 1 − precision | Hai cách hiểu cho độ khó chênh nhau rất xa: 360 FP so với khoảng 50 FP |
| D9 | Bản gốc của duplicate/rapid re-submission có bị coi là gian lận không | Thay đổi cả ground truth lẫn logic rule (flag claim nào trong cặp) |

---

## A. Dữ liệu và sinh dữ liệu

### D1. Seed và khả năng tái lập

- **Vấn đề:** Đề yêu cầu nộp kèm dataset nhưng không nói dữ liệu phải sinh lại được. Không có seed cố định thì metric thay đổi mỗi lần chạy, người chấm không tái hiện được kết quả, còn test tích hợp sẽ chập chờn.
- **Phương án:**
  - A. Seed cố định trong config, ví dụ `42`. *Đánh đổi:* tái lập hoàn toàn, nhưng dễ vô tình tối ưu cho đúng một seed.
  - B. Seed ngẫu nhiên mỗi lần, ghi seed vào output. *Đánh đổi:* trung thực hơn, nhưng file nộp và README khó khớp nhau.
  - C. Seed chính cố định để nộp, thêm một nhóm seed phụ để kiểm tra độ ổn định của metric. *Đánh đổi:* tốn thêm chút công báo cáo.
- **Đề xuất của Claude:** C. Tái lập được và chứng minh metric không phụ thuộc may rủi của một seed (liên quan M3).
- **Quyết định:** C. Seed chính cố định để tinh chỉnh (tuning), một seed khác làm held-out, thêm vài seed phụ để kiểm tra độ ổn định nếu còn thời gian. Lý do: tái lập được kết quả và chứng minh metric không phụ thuộc may rủi của một seed. Số seed phụ: 5 (Q6).

### D2. Định dạng file dataset

- **Vấn đề:** Đề chỉ liệt kê cột. `procedure_codes` là mảng nên CSV phải tự quy ước cách nối, còn JSON khó mở bằng spreadsheet. Định dạng này cũng là định dạng đầu vào mà engine phải đọc.
- **Phương án:**
  - A. CSV (`jackson-dataformat-csv`), `procedure_codes` nối bằng `|`. *Đánh đổi:* dễ mở bằng Excel, người chấm xem nhanh, nhưng phải tự parse mảng.
  - B. JSON array. *Đánh đổi:* giữ đúng kiểu dữ liệu và parse thẳng bằng Jackson, nhưng file 2.000 bản ghi khó đọc bằng mắt.
  - C. Xuất cả hai, engine đọc một định dạng chuẩn. *Đánh đổi:* thêm code xuất và nguy cơ hai file lệch nhau.
- **Đề xuất của Claude:** A. CSV là định dạng tự nhiên cho dataset nộp kèm, và `|` không xuất hiện trong mã ICD/procedure.
- **Quyết định:** A. CSV, `procedure_codes` nối bằng `|`. Lý do: dễ mở bằng Excel, người chấm xem nhanh, và `|` không xuất hiện trong mã ICD hay mã thủ thuật.

### D3. Lưu nhãn ground truth ở đâu

- **Vấn đề:** Metric cần biết claim nào là gian lận và thuộc pattern nào. Đặt nhãn chung file với dữ liệu đầu vào thì có nguy cơ rò rỉ nhãn: engine vô tình đọc nhãn, hoặc người chấm nghi ngờ điều đó.
- **Phương án:**
  - A. Thêm cột `is_fraud`, `fraud_pattern` vào `claims.csv`. *Đánh đổi:* đơn giản, nhưng lệch schema đề cho và dễ rò rỉ.
  - B. File riêng `ground_truth.csv` (`claim_id, is_fraud, fraud_pattern, related_claim_id`). Engine không bao giờ đọc file này, chỉ module metric đọc. *Đánh đổi:* thêm một file, nhưng tách bạch rõ ràng.
- **Đề xuất của Claude:** B. Giữ `claims.csv` đúng 10 cột của đề và chứng minh được là không rò rỉ nhãn.
- **Quyết định:** B. File riêng `ground_truth.csv` (`claim_id, is_fraud, fraud_pattern, related_claim_id`); `claims.csv` giữ đúng 10 cột của đề. Engine không bao giờ đọc file này, chỉ module metric đọc.

### D4. Đơn vị tiền và thang giá

- **Vấn đề:** Đề không nói đơn vị tiền. Ngưỡng 50.000 và ví dụ 85.000 gợi ý thang kiểu USD. Nếu là VND thì 50.000đ là con số vô lý. Thang giá quyết định claim loại nào có thể chạm ngưỡng, và do đó ảnh hưởng đến số FP tự nhiên của R9 (amount clustering).
- **Phương án:**
  - A. Không gắn đơn vị ("currency units"), số nguyên. Giá theo `claim_type`, ví dụ DENTAL 200–5.000, OUTPATIENT 300–15.000, INPATIENT 8.000–150.000. *Đánh đổi:* trung lập và khớp ví dụ trong đề.
  - B. Gắn USD, có phần lẻ cent. *Đánh đổi:* thực tế hơn, nhưng phải xử lý biên 49.999,50 (xem R9) và kiểu `BigDecimal`.
  - C. VND, nhân ngưỡng lên. *Đánh đổi:* hợp bối cảnh Papaya, nhưng lệch con số 50.000 cố định trong đề.
- **Đề xuất của Claude:** A. Dải "47,500–49,999" trong đề ngầm hiểu là số nguyên, nên tránh được rắc rối phần lẻ.
- **Quyết định:** A. Không gắn đơn vị tiền, dùng số nguyên, giá theo `claim_type`. Lý do: đề không nói đơn vị, 50.000 sẽ vô lý nếu là VND, và số nguyên tránh rắc rối biên khi có phần lẻ.

### D5. Danh mục mã chẩn đoán và thủ thuật

- **Vấn đề:** Cần danh mục ICD-10, procedure code (có cờ `surgical`, giá gốc, `claim_type`), danh sách bundle (R5) và whitelist chẩn đoán–thủ thuật (R8). Mã CPT thật có bản quyền AMA. Ví dụ trong đề dùng `P-1234`. Danh mục quá nhỏ làm mỗi nhóm procedure có ít mẫu, khiến upcoding kém ổn định. Danh mục quá lớn làm whitelist phình to.
- **Phương án:**
  - A. ICD-10 thật (công khai) cùng procedure code nội bộ `P-xxxx` kèm mô tả. Khoảng 20 chẩn đoán và 35–40 procedure. *Đánh đổi:* thực tế vừa đủ, không vướng bản quyền.
  - B. Toàn bộ mã giả. *Đánh đổi:* nhanh, nhưng mất tính "clinically associated" mà đề nhấn mạnh.
  - C. ICD-10 thật kèm CPT thật. *Đánh đổi:* thực tế nhất, nhưng có rủi ro bản quyền và tốn công tra cứu.
- **Đề xuất của Claude:** A. Danh mục để trong file JSON resource, dùng chung cho generator và engine với vai trò reference data. Generator không được dùng chung các ngưỡng phát hiện với engine (xem mục H).
- **Quyết định:** A. ICD-10 thật (công khai) và mã thủ thuật nội bộ `P-xxxx` (CPT thật có bản quyền); khoảng 20 chẩn đoán và 35-40 thủ thuật, đặt trong bốn file JSON resource `procedures.json`, `diagnoses.json`, `bundles.json`, `diagnosis-procedures.json`, dùng chung cho generator và engine. Generator không dùng chung các ngưỡng phát hiện với engine. Lưu ý: do D7, bảng ánh xạ chẩn đoán-thủ thuật phải phủ đủ mọi chẩn đoán trong danh mục; xem R8.

### D6. Phân phối member, provider và ngày

- **Vấn đề:** Đề ghi "~500 members", "~50 providers", ngày trong năm 2024. Phân phối ảnh hưởng trực tiếp đến nhiều rule:
  - Số claim mỗi provider quyết định độ tin cậy của tỉ lệ cuối tuần (R7).
  - Số claim mỗi member quyết định số rapid re-submission tự nhiên (R2).
  - Số mẫu mỗi procedure quyết định độ ổn định của upcoding (R4).
- **Phương án:**
  - A. Đều tuyệt đối. Trung bình 40 claim/provider và 4 claim/member. *Đánh đổi:* đơn giản, nhưng không thực tế, và provider nhỏ có quá ít lịch sử.
  - B. Lệch có kiểm soát: provider chia tầng (bệnh viện lớn, phòng khám, nha khoa), member theo phân phối lệch, ngày rải đều. *Đánh đổi:* thực tế hơn, phải cấu hình profile.
- **Đề xuất của Claude:** B, với đúng 500 member và 50 provider trong pool, tổng đúng 2.000 claim (≈ 1.800 hợp lệ và ≈ 200 gian lận). Mỗi provider có một profile riêng (loại hình, quy mô, tỉ lệ cuối tuần).
- **Quyết định:** B. Đúng 500 member, 50 provider, 2.000 claim (khoảng 1.800 hợp lệ và 200 gian lận); provider chia tầng (bệnh viện lớn, phòng khám, nha khoa), member theo phân phối lệch, ngày rải đều trong năm. Lưu ý: provider 'chỉ làm ngày thường' cần đủ lịch sử (D11, R7).

### D7. Nhất quán giữa claim_type, chẩn đoán, thủ thuật và provider

- **Vấn đề:** Đề không ràng buộc các trường này với nhau. Nếu sinh độc lập sẽ xuất hiện claim DENTAL có thủ thuật phẫu thuật tim, hoặc phòng khám nha khoa làm ca nội trú. Khi đó R8 bắn FP hàng loạt, và dataset trông thiếu tin cậy.
- **Phương án:**
  - A. Sinh theo chuỗi: provider (loại hình) → `claim_type` → chẩn đoán → thủ thuật hợp lệ → giá. *Đánh đổi:* nhất quán, nhưng code generator dài hơn.
  - B. Sinh độc lập rồi lọc bỏ tổ hợp vô lý. *Đánh đổi:* dễ viết, nhưng phân phối bị méo và khó kiểm soát số lượng.
- **Đề xuất của Claude:** A. Đây là điều kiện để R8 có ý nghĩa và để metric phản ánh đúng chất lượng rule.
- **Quyết định:** A. Sinh theo chuỗi: provider (loại hình) rồi `claim_type` rồi chẩn đoán rồi thủ thuật hợp lệ rồi giá. Lý do: để luật mismatch (R8) có ý nghĩa và không báo oan hàng loạt do dữ liệu vô lý.

### D8. Mô hình số tiền của claim hợp lệ

- **Vấn đề:** Đề chỉ ghi "varies by procedure". Kiểu nhiễu quyết định số FP tự nhiên của upcoding:
  - Nhiễu chuẩn: khoảng 2,3% claim hợp lệ vượt 2σ, tức khoảng 40 FP.
  - Nhiễu đều ±20%: về mặt toán học không bao giờ vượt 2σ, tức 0 FP. Đây là cách làm metric đẹp giả.
  - Lognormal: đuôi phải dày, nên FP nhiều hơn.
- **Phương án:**
  - A. Giá gốc × nhiễu chuẩn cắt cụt (CV khoảng 15%, cắt ±3σ). *Đánh đổi:* thực tế, chấp nhận khoảng 2% FP tự nhiên của upcoding.
  - B. Giá gốc × nhiễu đều. *Đánh đổi:* metric rất đẹp, nhưng thiếu trung thực và người chấm tinh ý sẽ nhận ra.
  - C. Lognormal. *Đánh đổi:* thực tế nhất với chi phí y tế, nhưng FP upcoding cao, có thể làm precision tụt mạnh.
- **Đề xuất của Claude:** A, và ghi rõ trong báo cáo rằng FP của upcoding là "nhiễu thống kê có chủ đích".
- **Quyết định:** A. Giá gốc nhân nhiễu chuẩn cắt cụt (CV khoảng 15%, cắt ±3σ). Lý do: thực tế, và chấp nhận khoảng 2% báo oan tự nhiên của upcoding (khoảng 40 claim); ghi rõ trong báo cáo đây là 'nhiễu thống kê có chủ đích'. Lưu ý: cùng với R9 (20-40 claim hợp lệ trong dải clustering), FDR có thể vượt 20% trong khi FPR chuẩn vẫn thấp; ghi trong báo cáo (M1).

### D9. Bản gốc của duplicate và rapid re-submission có tính là gian lận?

- **Vấn đề:** "30 duplicate claims" cần 30 bản gốc đi kèm. Nếu chỉ bản sao là gian lận mà rule flag cả cặp, thì 30 bản gốc thành FP. Nếu gán nhãn gian lận cho cả bản gốc, số gian lận tăng lên 60 và recall bị thổi phồng. Rapid re-submission cũng vậy.
- **Phương án:**
  - A. Chỉ bản sao/bản nộp lại là gian lận. Rule chỉ flag lần xuất hiện sau (theo thứ tự ở D14). *Đánh đổi:* đúng bản chất (bản gốc là chi trả hợp lệ), nhưng rule phải biết thứ tự.
  - B. Cả cặp là gian lận, rule flag cả hai. *Đánh đổi:* rule đơn giản, nhưng lệch con số "~200" và làm metric đẹp giả.
  - C. Chỉ bản sao là gian lận nhưng rule flag cả hai. *Đánh đổi:* trung thực, nhưng chấp nhận 60 FP chắc chắn.
- **Đề xuất của Claude:** A. Nghiệp vụ đúng nhất: bản gốc vẫn cần được chi trả, chỉ bản lặp mới cần chặn.
- **Quyết định:** A. Claim đầu có thể là claim thật nên chỉ bản sao/bản nộp lại bị coi là gian lận; flag hoặc gán nhãn cả nhóm sẽ làm oan một claim hợp lệ.

### D10. ⚠️ Mâu thuẫn ở phantom billing: 25 claim so với ngưỡng > 30 claim/ngày

- **Vấn đề:** Rule chỉ kích hoạt khi một provider có ít nhất 31 claim trong cùng một ngày. Muốn có đúng 25 claim gian lận thì phải thêm ít nhất 6 claim hợp lệ cùng ngày, và rule sẽ flag cả 31, tức 6 FP. Ngoài ra, với 2.000 claim, 50 provider và 366 ngày, trung bình mỗi provider chỉ có khoảng 0,1 claim/ngày, nên dữ liệu tự nhiên không bao giờ chạm ngưỡng.
- **Phương án:**
  - A. Một provider-ngày có 31 claim, gán nhãn phantom cho cả 31 (tổng gian lận khoảng 206). Ghi rõ đây là sai lệch có chủ đích so với con số 25. *Đánh đổi:* nhất quán với rule, nhưng lệch số đếm của đề.
  - B. 25 claim phantom cùng 6 claim hợp lệ có sẵn trong ngày đó. Rule flag cả 31. *Đánh đổi:* giữ đúng con số 25, chấp nhận 6 FP không tránh được.
  - C. Rule chỉ flag các claim từ thứ 31 trở đi. *Đánh đổi:* chỉ bắt được khoảng 1 claim, recall phantom gần 0.
- **Đề xuất của Claude:** A. Trong nghiệp vụ, khi lưu lượng một ngày là phi lý thì toàn bộ claim của ngày đó đều đáng nghi. Ghi rõ lý do lệch số đếm trong README và báo cáo.
- **Quyết định:** B. 25 claim phantom nhúng cùng ít nhất 6 claim hợp lệ trong ngày đó; rule flag cả 31 và các claim thường được tính là false positive. Lý do: giữ đúng số 200 của đề và con số thận trọng hơn. Lưu ý: khác đề xuất A của Claude. Trong writeup cần ghi: luật cũng flag claim thường cùng ngày, làm precision ở mức claim giảm.

### D11. ⚠️ Weekend anomaly: "never" so với "< 5%", và lịch sử quá mỏng

- **Vấn đề:** Phần dataset ghi "providers who never operate on weekends", còn rule ghi "< 5% historical weekend volume". Có hai hệ quả:
  - Trung bình 40 claim/provider, nên 5% tương đương chỉ 2 claim. Nếu dồn 20 claim gian lận cuối tuần vào vài provider, chính chúng làm tỉ lệ vượt 5% và rule không bắt được.
  - Đề không nói lịch sử tính trên tất cả claim hay chỉ claim phẫu thuật, và có tính cả claim đang xét hay không.
- **Phương án:**
  - A. Chọn khoảng 8–10 provider phẫu thuật "chỉ làm ngày thường", mỗi provider ít nhất 60 claim, không có claim hợp lệ nào vào cuối tuần, tối đa 2–3 claim gian lận mỗi provider. Rule tính tỉ lệ leave-one-out (bỏ claim đang xét). *Đánh đổi:* bắt được hết, nhưng phải thiết kế phân phối khá chặt.
  - B. Tính tỉ lệ cuối tuần chỉ trên claim không phẫu thuật. *Đánh đổi:* tránh được vòng lặp, nhưng lệch chữ "historical weekend volume".
  - C. Dồn tất cả vào 2–3 provider và tính tỉ lệ có cả claim đang xét. *Đánh đổi:* đơn giản, nhưng tỉ lệ vượt 5% và recall weekend tụt mạnh.
- **Đề xuất của Claude:** A. Bám đúng ngưỡng "< 5%" của rule, còn "never" chỉ là cách mô tả profile dữ liệu.
- **Quyết định:** A, theo hướng: tính tỉ lệ cuối tuần của provider loại chính claim đang xét (leave-one-out) và rải claim cuối tuần nhúng thưa. Lý do: mỗi provider chỉ khoảng 40 claim, chỉ 2 claim cuối tuần đã đạt 5%

### D12. Chồng lấn pattern và claim hợp lệ vô tình kích hoạt rule

- **Vấn đề:** Một claim gian lận có thể vô tình khớp nhiều pattern. Ví dụ claim upcoded có số tiền rơi vào dải 47.500–49.999, hoặc claim unbundled bị coi là upcoded. Claim hợp lệ cũng có thể vô tình kích hoạt rule. Cách xử lý quyết định metric có trung thực hay không.
- **Phương án:**
  - A. Mỗi claim gian lận mang đúng một pattern chính và được kiểm tra không khớp pattern khác. Claim hợp lệ tự nhiên kích hoạt rule thì giữ nguyên và tính là FP. *Đánh đổi:* quy gán rõ ràng, metric trung thực.
  - B. Lọc sạch mọi claim hợp lệ kích hoạt rule. *Đánh đổi:* FP gần 0, là metric đẹp giả.
  - C. Cho phép chồng lấn tự nhiên, nhãn lưu nhiều pattern. *Đánh đổi:* thực tế, nhưng báo cáo theo pattern phức tạp hơn.
- **Đề xuất của Claude:** A. Sau khi sinh, kiểm tra tự động số lượng từng pattern và ghi số FP tự nhiên vào báo cáo.
- **Quyết định:** A. Mỗi claim gian lận mang đúng một pattern chính và được kiểm tra không khớp pattern khác (ví dụ claim upcoding không để số tiền rơi vào dải clustering). Claim hợp lệ tự nhiên kích hoạt luật thì giữ nguyên và tính là false positive. Lý do: quy gán rõ ràng, metric trung thực.

### D13. Có sinh "hard negatives" (claim hợp lệ sát biên) không?

- **Vấn đề:** Nếu dữ liệu hợp lệ nằm xa mọi ngưỡng thì precision gần 100% chẳng chứng minh được gì. Các ca sát biên mới kiểm tra rule có đúng biên không và cho precision có ý nghĩa. Ví dụ:
  - số tiền 47.400 hoặc 50.000;
  - tái khám đúng 8 ngày;
  - provider có 30 claim trong một ngày;
  - z = 1,9;
  - bundle thiếu một thành phần;
  - ca mổ cuối tuần ở provider bình thường;
  - lỗi mã hoá nhẹ.
- **Phương án:**
  - A. Thêm khoảng 50–80 hard negatives có kiểm soát, gắn nhãn `legit_near_miss` trong ground truth. *Đánh đổi:* metric đáng tin hơn, tốn công generator.
  - B. Không thêm. *Đánh đổi:* nhanh, nhưng metric dễ bị coi là tự chấm điểm cho mình.
  - C. Thêm cả claim hợp lệ khớp rule thực sự (tái khám bệnh mạn tính trong 7 ngày, lỗi mã hoá). *Đánh đổi:* trung thực nhất, có thể kéo precision xuống mức thực tế.
- **Đề xuất của Claude:** A cộng một phần nhỏ của C (khoảng 20–30 claim), báo cáo riêng. Đây là điểm cộng lớn khi người chấm hỏi "metric có thật không".
- **Quyết định:** A với số lượng nhỏ hơn (khoảng 30-50 claim hợp lệ sát biên, gắn nhãn `legit_near_miss` trong ground truth). Phần C (claim hợp lệ khớp luật thật sự, như tái khám bệnh mạn tính) ghi là hướng mở rộng nếu còn thời gian. Lưu ý: 20-40 claim hợp lệ trong dải clustering (R9) đã đóng vai một phần C. Lý do chọn số nhỏ: thời gian hẹp (3 ngày).

### D14. Thứ tự claim và quy ước "lần nộp sau"

- **Vấn đề:** Dữ liệu không có timestamp nộp, chỉ có `claim_date`. Để chỉ flag lần xuất hiện sau (D9, R1, R2) cần một thứ tự xác định, nhất là khi duplicate trùng ngày.
- **Phương án:**
  - A. Sau khi sinh, sắp theo `claim_date` rồi gán `claim_id` tuần tự (`CLM-00001`…). Bản sao luôn đứng sau bản gốc. Rule dùng thứ tự (`claim_date`, `claim_id`). *Đánh đổi:* đơn giản, tất định.
  - B. Thêm cột `submitted_at`. *Đánh đổi:* rõ ràng hơn, nhưng lệch schema.
- **Đề xuất của Claude:** A. Giữ đúng schema, và claim_id tăng dần là proxy hợp lý cho thứ tự nộp.
- **Quyết định:** A. Sau khi sinh, sắp theo `claim_date` rồi gán `claim_id` tuần tự; luật dùng thứ tự (`claim_date`, `claim_id`), nên bản sao luôn đứng sau bản gốc. Lý do: giữ đúng schema của đề, tất định.

---

## B. Các rule phát hiện

### R1. Duplicate claim

- **Vấn đề:** Khoá khớp là member + provider + date + diagnosis. Cần chốt:
  - flag cả nhóm hay chỉ các bản sau;
  - có bắt buộc trùng cả procedure/số tiền không;
  - nhóm có 3 bản trở lên xử lý thế nào.

  Trường hợp hợp lệ hiếm nhưng có thật: một ngày khám rồi làm xét nghiệm cùng chẩn đoán.
- **Phương án:**
  - A. Khớp đúng 4 khoá, flag mọi claim trừ claim đầu nhóm. Evidence nêu `claim_id` gốc. *Đánh đổi:* đúng chữ của đề, có thể bắt nhầm ca khám cùng ngày hợp lệ.
  - B. Khớp 4 khoá và cùng tập procedure. *Đánh đổi:* ít FP hơn, nhưng bỏ sót duplicate "đổi nhẹ procedure".
- **Đề xuất của Claude:** A. Bám định nghĩa của đề. Nhóm 3 bản trở lên thì flag bản thứ 2 trở đi, evidence ghi số lần lặp.
- **Quyết định:** A. Flag mọi claim trừ claim đầu nhóm; claim đầu có thể là claim thật nên flag cả nhóm sẽ làm oan một claim hợp lệ.

### R2. Rapid re-submission

- **Vấn đề:** Cần chốt các điểm sau:
  - "within 7 days" là 0–7, 1–7 hay < 7 ngày;
  - có tính khi khác provider không;
  - cặp cùng ngày cùng provider đã là duplicate thì có flag thêm rapid không (cộng điểm hai lần);
  - chuỗi 3 claim thì flag claim nào.
- **Phương án:**
  - A. Chênh 0–7 ngày (có tính 7), bất kể provider. Bỏ qua cặp đã là duplicate. Flag claim sau, so với claim gần nhất trước nó. *Đánh đổi:* tránh tính điểm kép, bắt được cả trường hợp đổi provider.
  - B. Chênh 1–7 ngày, ngày 0 để duplicate lo. *Đánh đổi:* đơn giản, nhưng bỏ sót trường hợp cùng ngày khác provider.
  - C. Không loại trừ duplicate. *Đánh đổi:* đơn giản nhất, nhưng duplicate bị cộng điểm kép.
- **Đề xuất của Claude:** A. "Within 7 days" hiểu tự nhiên là ≤ 7, và loại trừ chồng lấn giúp trọng số phản ánh đúng ý nghĩa.
- **Quyết định:** A. Rapid loại các cặp đã khớp duplicate để tránh đếm đôi một hành vi và để mỗi luật có ý nghĩa riêng (duplicate là nộp trùng, rapid là khai lại sau đó), nên precision từng luật đo đúng.

### R3. ⚠️ Upcoding: gán số tiền cho procedure nào?

- **Vấn đề:** Một claim có `procedure_codes[]` nhưng chỉ có một `submitted_amount`. "Mean for that procedure code" không xác định được với claim nhiều procedure. Nếu nhóm theo procedure đầu tiên, claim unbundled (số tiền là tổng các thành phần) sẽ bị coi là upcoded. Đây là chồng lấn giả.
- **Phương án:**
  - A. Nhóm theo procedure chính (phần tử đầu). *Đánh đổi:* đơn giản, nhưng nhiễu với claim nhiều procedure.
  - B. Nhóm theo chữ ký tập procedure (sorted set). Nếu nhóm có ít hơn `n_min` mẫu thì lùi về nhóm procedure chính, vẫn thiếu thì bỏ qua và ghi chú. *Đánh đổi:* so sánh đúng loại, logic phức tạp hơn.
  - C. Giới hạn generator: đa số claim chỉ có một procedure, claim nhiều procedure chỉ theo vài tổ hợp chuẩn. Rule nhóm theo chữ ký. *Đánh đổi:* đơn giản cả hai phía, dữ liệu kém đa dạng hơn.
- **Đề xuất của Claude:** B kết hợp C. Generator giữ phần lớn claim chỉ có một procedure, rule nhóm theo chữ ký và có fallback.
- **Quyết định:** B kết hợp C.

### R4. ⚠️ Upcoding: cách tính mean/SD

- **Vấn đề:** Nếu tính mean/SD có cả claim đang xét, outlier tự làm phình SD (masking). Với n mẫu, z-score tối đa của chính nó là (n−1)/√n, nên khi n = 4 thì z không thể vượt 1,5. Cũng cần chốt SD của mẫu hay của tổng thể, cỡ mẫu tối thiểu, và trường hợp SD = 0.
- **Phương án:**
  - A. Mean/SD trên toàn nhóm kể cả claim đang xét. *Đánh đổi:* đúng chữ của đề, nhưng recall upcoding giảm ở nhóm nhỏ.
  - B. Leave-one-out mean và SD mẫu, `n_min = 10`, bỏ qua khi SD = 0. *Đánh đổi:* chuẩn thống kê, tốn thêm chút tính toán (vẫn O(n)).
  - C. Median/MAD (robust). *Đánh đổi:* bền nhất với outlier, nhưng lệch chữ "standard deviations" của đề.
- **Đề xuất của Claude:** B. Vẫn đúng "mean + 2 SD" của đề, lại tránh được masking.
- **Quyết định:** A. Sau khi đo metric (Task 17), nếu recall của riêng luật upcoding thấp rõ rệt thì chuyển sang C (median/MAD).
- **Quyết định Task 17 (2026-10-25, trên dataset tuning seed 42):** R4: giữ R4-A (z-score, thống kê gồm cả claim đang xét). Lý do: Tiêu chí đổi sang median/MAD đặt trước là recall upcoding < 70% trên tuning; thực tế 86,7% (26/30) nên không kích hoạt. Bốn claim sót là ba claim tăng nhẹ và CLM-01130 bị masking, là giới hạn đã biết của z-score (L9). Đổi rule sau khi đã thấy số là chỉnh cho đẹp.

### R5. Unbundling

- **Vấn đề:** Cần chốt:
  - kích hoạt khi claim chứa đủ mọi thành phần của bundle, hay chỉ cần ≥ 2 thành phần;
  - claim có cả mã bundle lẫn thành phần thì sao;
  - tách thành phần ra nhiều claim cùng ngày có tính không.

  Ngoài ra, thành phần phải nằm trong whitelist chẩn đoán (R8), nếu không claim unbundled sẽ kích hoạt thêm mismatch.
- **Phương án:**
  - A. Kích hoạt khi một claim chứa đủ mọi thành phần của một bundle (tối thiểu 5 bundle, mỗi bundle 2–4 thành phần). Thiếu thành phần thì không flag. *Đánh đổi:* chính xác, ít FP, bỏ sót unbundle một phần.
  - B. Kích hoạt khi có ≥ 2 thành phần. *Đánh đổi:* bắt rộng hơn, FP cao hơn.
  - C. A cộng phát hiện chia nhỏ qua nhiều claim cùng member, provider, ngày. *Đánh đổi:* bao quát hơn, nhưng chồng lấn với duplicate và phức tạp.
- **Đề xuất của Claude:** A, có thêm trường hợp "mã bundle cùng thành phần trên một claim" (thanh toán kép). Trường hợp chia qua nhiều claim ghi vào giới hạn đã biết.
- **Quyết định:** A. Kích hoạt khi một claim chứa đủ mọi thành phần của một bundle (tối thiểu 5 bundle, mỗi bundle 2-4 thành phần); thiếu thành phần thì không flag. Thêm trường hợp 'mã bundle cùng thành phần trên một claim' (thanh toán kép). Trường hợp chia thành phần qua nhiều claim cùng member, provider, ngày ghi vào giới hạn đã biết. Lưu ý: các thành phần phải nằm trong whitelist chẩn đoán (R8) để claim unbundled không kích hoạt thêm mismatch.

### R6. Phantom billing

- **Vấn đề:** "> 30 claims in a single day". Cần chốt flag tất cả claim của provider trong ngày đó hay chỉ phần vượt ngưỡng. Lựa chọn này gắn với D10.
- **Phương án:**
  - A. Flag mọi claim của provider-ngày khi số lượng > 30. *Đánh đổi:* nhất quán với D10-A.
  - B. Chỉ flag các claim từ thứ 31 trở đi. *Đánh đổi:* gần như không có recall.
- **Đề xuất của Claude:** A. Evidence nêu số claim trong ngày so với trung bình mỗi ngày của provider đó.
- **Quyết định:** A. Flag mọi claim của provider-ngày khi số lượng > 30; các claim thường cùng ngày bị flag và được tính là false positive (xem D10).

### R7. Weekend anomaly

- **Vấn đề:** Cần chốt:
  - "surgical procedure" là gì;
  - mẫu số của tỉ lệ cuối tuần là tất cả claim hay chỉ claim phẫu thuật;
  - có leave-one-out không;
  - provider có ít lịch sử thì sao;
  - claim có một procedure phẫu thuật lẫn trong nhiều procedure có tính không.
- **Phương án:**
  - A. Cờ `surgical` trong danh mục procedure. Tỉ lệ = claim cuối tuần / tất cả claim của provider, leave-one-out. Lịch sử tối thiểu 20 claim. Kích hoạt nếu có ít nhất một procedure phẫu thuật. *Đánh đổi:* bám sát đề, mẫu số đủ lớn.
  - B. Mẫu số chỉ gồm claim phẫu thuật. *Đánh đổi:* đúng chuyên môn hơn, nhưng mẫu số quá nhỏ nên tỉ lệ dao động mạnh.
- **Đề xuất của Claude:** A. Ngày cuối tuần là Thứ Bảy và Chủ Nhật, không tính ngày lễ.
- **Quyết định:** A. Cờ `surgical` nằm trong danh mục procedure (`procedures.json`); tỉ lệ cuối tuần của provider tính leave-one-out (loại chính claim đang xét). Lý do: mỗi provider chỉ khoảng 40 claim, chỉ 2 claim cuối tuần đã đạt 5%.

### R8. Diagnosis-procedure mismatch

- **Vấn đề:** Đề chỉ yêu cầu tối thiểu 10 cặp hợp lệ (whitelist). Nếu whitelist chỉ có 10 cặp mà dữ liệu dùng 20 chẩn đoán, gần như mọi claim sẽ bị flag. Cần chốt thêm:
  - mismatch khi có bất kỳ procedure nào sai hay khi tất cả đều sai;
  - chẩn đoán không có trong map thì xử lý thế nào.
- **Phương án:**
  - A. Whitelist phủ đủ mọi chẩn đoán trong danh mục (khoảng 20 chẩn đoán, 50–60 cặp). Flag nếu có bất kỳ procedure nào không nằm trong tập hợp lệ. Chẩn đoán lạ thì không flag và ghi cảnh báo. *Đánh đổi:* chính xác. Evidence liệt kê procedure lạc.
  - B. Như A nhưng chẩn đoán lạ cũng flag. *Đánh đổi:* an toàn hơn về phía bắt gian lận, nhưng FP khi danh mục thiếu.
  - C. Flag chỉ khi mọi procedure đều sai. *Đánh đổi:* ít FP, nhưng bỏ sót trường hợp nhét thêm một procedure lạc.
- **Đề xuất của Claude:** A. Thủ thuật hành chính chung (ví dụ phí khám) được phép đi với mọi chẩn đoán để tránh FP vô nghĩa.
- **Quyết định:** A. Chỉ flag khi chẩn đoán CÓ trong bảng ánh xạ và procedure không hợp lệ; chẩn đoán không có trong bảng thì chưa đủ bằng chứng để kết luận, còn flag chẩn đoán lạ thì gần như mọi claim bị flag và false positive bùng nổ.

### R9. Amount clustering

- **Vấn đề:** Đề viết "47,500–49,999". Nếu số tiền có phần lẻ thì 49.999,50 có tính không? Cũng cần chốt có áp dụng cho mọi `claim_type` không, và ngưỡng có cấu hình được không.
- **Phương án:**
  - A. `47_500 ≤ amount < 50_000`, áp cho mọi loại claim, ngưỡng và tỉ lệ 5% cấu hình được. *Đánh đổi:* bao trọn cả khi có phần lẻ.
  - B. `47_500 ≤ amount ≤ 49_999`. *Đánh đổi:* đúng chữ của đề, hở khoảng 49.999–50.000 nếu có phần lẻ.
- **Đề xuất của Claude:** A. Tương đương B khi số tiền là số nguyên (D4-A), lại bền với phần lẻ.
- **Quyết định:** A. Khoảng 1-2% claim hợp lệ (khoảng 20-40 claim) được cố tình đặt trong 47.500-49.999 để tạo nhiễu thực tế, vì nếu không có nhiễu thì luật bắt đúng 100% một cách giả tạo. Với số nguyên, khoảng này tương đương 47.500 ≤ amount ≤ 49.999 (R10).

### R10. Quy ước biên (strict hay inclusive) cho mọi rule

- **Vấn đề:** Sai một dấu `>` hay `≥` có thể làm lệch hàng loạt claim sát biên (D13), và test biên phải khớp với quy ước.
- **Phương án:**
  - A. Bám nguyên chữ của đề, như bảng dưới. *Đánh đổi:* dễ giải thích với người chấm.
  - B. Tự điều chỉnh cho thuận nghiệp vụ. *Đánh đổi:* phải giải trình từng chỗ.

  | Rule | Kích hoạt | Không kích hoạt |
  |------|-----------|-----------------|
  | Rapid re-submission | chênh ≤ 7 ngày | 8 ngày |
  | Upcoding | z > 2,0 | z = 2,0 |
  | Phantom billing | 31 claim/ngày | 30 claim/ngày |
  | Weekend anomaly | tỉ lệ < 5% | tỉ lệ = 5% |
  | Amount clustering | 47.500 | 47.499 và 50.000 |
- **Đề xuất của Claude:** A. Mỗi biên có một cặp test (vừa kích hoạt / vừa không).
- **Quyết định:** A. Bám nguyên chữ của đề: rapid chênh ≤ 7 ngày kích hoạt (8 ngày không); upcoding z > 2,0 (z = 2,0 không); phantom 31 claim/ngày kích hoạt (30 không); weekend tỉ lệ < 5% kích hoạt (= 5% không); clustering 47.500 kích hoạt (47.499 và 50.000 không). Mỗi biên có một cặp test (vừa kích hoạt, vừa không). Quyết định này cũng chốt biên cho R2 (≤ 7 ngày) và R9 (với số nguyên: 47.500 ≤ amount ≤ 49.999).

### R11. Dữ liệu đầu vào lỗi hoặc không nhất quán

- **Vấn đề:** Engine nên chạy được với dữ liệu không do mình sinh ra. Các ca cần xử lý:
  - `is_weekend` mâu thuẫn với `claim_date`;
  - `procedure_codes` rỗng;
  - số tiền ≤ 0;
  - `claim_id` trùng;
  - mã không có trong danh mục.
- **Phương án:**
  - A. Validate khi load: tính lại `is_weekend` từ ngày (cảnh báo nếu lệch), loại dòng lỗi nặng kèm báo cáo, mã lạ thì giữ lại và cảnh báo. *Đánh đổi:* chắc chắn hơn, thêm code và test.
  - B. Tin dữ liệu tuyệt đối. *Đánh đổi:* nhanh, nhưng gặp dữ liệu bẩn là crash hoặc cho kết quả sai.
- **Đề xuất của Claude:** A, ở mức tối thiểu. Đây cũng là nguồn tốt cho các test "edge case" mà đề yêu cầu.
- **Quyết định:** A, ở mức tối thiểu. Validate khi load: tính lại `is_weekend` từ `claim_date` (cảnh báo nếu lệch), loại dòng lỗi nặng (số tiền ≤ 0, `claim_id` trùng, `procedure_codes` rỗng) kèm báo cáo, mã không có trong danh mục thì giữ lại và cảnh báo. Lý do: engine chạy được với dữ liệu không do mình sinh ra, và đây là nguồn tốt cho các test edge case.

### R12. Dùng dữ liệu "tương lai" khi tính thống kê

- **Vấn đề:** Upcoding, weekend và rapid dùng thống kê cả năm, nên một claim tháng 1 được đánh giá bằng dữ liệu tháng 12. Với chấm điểm theo lô thì chấp nhận được, nhưng đây là rò rỉ dữ liệu theo thời gian nếu coi engine là hệ thống chạy trước khi thanh toán.
- **Phương án:**
  - A. Theo lô, thống kê trên toàn dataset, ghi rõ giả định. *Đánh đổi:* đơn giản, đúng tinh thần "analyzes claims" của đề.
  - B. Theo thời gian: chỉ dùng dữ liệu trước claim đang xét. *Đánh đổi:* thực tế hơn, nhưng claim đầu năm thiếu lịch sử và recall giảm.
- **Đề xuất của Claude:** A. Ghi giới hạn này trong README và nêu B là hướng mở rộng.
- **Quyết định:** A. Chấm theo lô, thống kê trên toàn dataset, ghi rõ giả định trong README; xử lý theo thời gian (chỉ dùng dữ liệu trước claim đang xét) ghi là hướng mở rộng. Lý do: đơn giản và đúng tinh thần 'analyzes claims' của đề.

---

## C. Chấm điểm

### S1. ⚠️ Công thức chuẩn hoá điểm 0–100

- **Vấn đề:** "Weighted sum of triggered rules, normalized to 0–100" không nói chuẩn hoá theo gì.
  - Chia cho tổng mọi trọng số (khoảng 28): một rule nặng nhất chỉ được khoảng 18 điểm, và chỉ đạt 100 khi cả 8 rule cùng kích hoạt, điều gần như không xảy ra.
  - Ví dụ trong đề có 2 flag (4 + 2) cho 78 điểm, không khớp cách trên.
- **Phương án:**
  - A. `100 × Σw_triggered / Σw_all`. *Đánh đổi:* đúng chữ nhất, nhưng điểm thấp và khó đọc (duplicate chắc chắn chỉ được khoảng 18).
  - B. `min(100, 100 × Σw_triggered / S_ref)`, với `S_ref` cấu hình được (ví dụ 10, tương đương "hai rule mạnh"). *Đánh đổi:* vẫn là weighted sum, dễ đọc, nhưng điểm bão hoà ở 100 nên nhiều claim đồng hạng ở đỉnh.
  - C. Noisy-OR: `100 × (1 − Π(1 − k·w_i))`, ví dụ k = 0,15, cho (4, 2) khoảng 72. *Đánh đổi:* bị chặn tự nhiên, lợi ích giảm dần, khớp ví dụ, nhưng không còn là "weighted sum" theo đúng nghĩa.
- **Đề xuất của Claude:** B. Giữ đúng "weighted sum" của đề và cho điểm có ý nghĩa nghiệp vụ. `S_ref` và công thức được giải thích trong README.
- **Quyết định:** A. score = round(100 × Σw_triggered / Σw_all). Tổng trọng số hiện là 28 và phải cập nhật nếu đổi trọng số. Hệ quả: claim chỉ vi phạm một luật có điểm thấp, nên cần ngưỡng T (S3). Lưu ý: khác đề xuất B của Claude.

### S2. `severity` trong flag: tĩnh hay theo mức độ bằng chứng?

- **Vấn đề:** Ví dụ trong đề có upcoding severity 4 và clustering severity 2. Không rõ severity là trọng số cấu hình của rule, hay thay đổi theo bằng chứng (ví dụ z = 2,8 cho 4, z = 6 cho 5).
- **Phương án:**
  - A. `severity` = trọng số cấu hình của rule. *Đánh đổi:* đơn giản, đúng "configurable severity weight", minh bạch.
  - B. Thay đổi theo bằng chứng, chặn trên bởi trọng số. *Đánh đổi:* tinh tế hơn, nhưng thêm tham số và khó giải trình trọng số.
- **Đề xuất của Claude:** A. Độ mạnh của bằng chứng đã nằm trong chuỗi `evidence`.
- **Quyết định:** A. `severity` của flag bằng trọng số cấu hình của rule. Lý do: đơn giản, minh bạch, đúng 'configurable severity weight'; độ mạnh của bằng chứng đã nằm trong chuỗi `evidence`.

### S3. Ngưỡng phân loại "fraud" để tính metric

- **Vấn đề:** Metric cần nhãn dự đoán nhị phân, nhưng engine chỉ cho điểm. Gần như mọi claim gian lận sinh ra chỉ kích hoạt một rule (D12-A). Nếu ngưỡng T cao hơn điểm của một rule nhẹ, ví dụ clustering trọng số 2 cho 20 điểm, thì cả pattern đó bị loại: 20/200 = 10% recall mỗi pattern.
- **Phương án:**
  - A. T mặc định sao cho mọi rule đơn lẻ đều vượt ngưỡng (tương đương "có flag là dự đoán gian lận"), kèm bảng metric theo nhiều ngưỡng. *Đánh đổi:* recall cao. Trọng số tác động lên thứ hạng nhiều hơn lên metric.
  - B. T cao hơn (ví dụ 40), chỉ claim có rule mạnh hoặc nhiều rule mới bị coi là gian lận. *Đánh đổi:* precision cao, recall có thể dưới 80% vì mất trọn các pattern nhẹ.
  - C. Chọn T tối ưu F1 trên seed tuning, báo cáo trên seed khác. *Đánh đổi:* bài bản, tốn công hơn.
- **Đề xuất của Claude:** A cùng bảng ngưỡng (precision/recall/FPR tại T = 10, 20, …, 100). Minh bạch, và cho thấy rõ đánh đổi giữa trọng số và ngưỡng.
- **Quyết định:** A. Trên dataset tuning, chọn T là giá trị cho recall cao nhất mà FPR vẫn ≤ 20%; rồi đóng băng T và trọng số, chỉ đo trên dataset held-out để không tự chấm bài đã biết đáp án.
- **Quyết định Task 17 (2026-10-25, trên dataset tuning seed 42):** T = 7. Lý do: Giữ tiêu chí đã chốt trước khi chạy: T là giá trị cho recall cao nhất với FPR chuẩn <= 20% (đúng chữ của đề). Trên tuning T = 7 cho recall 98,0%, FPR 3,3%. FDR 23,4% không đạt nếu hiểu "FPR" theo nghĩa tỉ lệ cảnh báo sai; mình báo cáo cả hai cách hiểu thay vì đổi tiêu chí. T = 11 qua cả hai cách hiểu (FDR 9,3%) nhưng recall còn 73,0% (biên 3 điểm), rapid và clustering không bao giờ được bắt khi đứng một mình, và vi phạm mục tiêu nội bộ recall >= 85%.

### S4. Cấu hình trọng số và tham số rule

- **Vấn đề:** Đề yêu cầu trọng số "configurable". Các tham số rule (7 ngày, 2σ, 30 claim, 5%, 50.000, 5%) cũng nên cấu hình được để thử độ nhạy.
- **Phương án:**
  - A. File `fraud-config.json` trong resources, có thể ghi đè bằng đường dẫn qua CLI. Chứa trọng số, tham số rule, `S_ref`, ngưỡng T. *Đánh đổi:* tập trung, dùng luôn Jackson.
  - B. Hằng số trong code. *Đánh đổi:* nhanh, nhưng không đáp ứng "configurable".
  - C. `.properties`. *Đánh đổi:* đơn giản, nhưng khó biểu diễn map lồng nhau.
- **Đề xuất của Claude:** A. Một nguồn duy nhất, dễ review.
- **Quyết định:** A. File `fraud-config.json` trong resources, có thể ghi đè bằng đường dẫn qua CLI; chứa trọng số, tham số rule (7 ngày, 2σ, 30 claim, 5%, 50.000, 5%); ngưỡng T không nằm trong file này mà chỉ có trong `config/frozen-config.json` do lệnh `tune` ghi (Task 17). Lý do: một nguồn duy nhất, dễ review, dùng luôn Jackson. Danh mục mã không nằm trong file này mà ở bốn file riêng (D5). Config sau khi tuning được đóng băng vào `config/frozen-config.json` (Task 17).

### S5. Xếp hạng và phá hoà

- **Vấn đề:** Nhiều claim cùng điểm (đặc biệt khi bão hoà, S1-B). Thứ tự phải tất định để output và test ổn định.
- **Phương án:**
  - A. Điểm giảm dần, rồi số flag giảm dần, rồi `submitted_amount` giảm dần, rồi `claim_id` tăng dần. *Đánh đổi:* claim nhiều tiền hơn được ưu tiên điều tra, đúng tinh thần nghiệp vụ.
  - B. Điểm giảm dần, rồi `claim_id`. *Đánh đổi:* đơn giản.
- **Đề xuất của Claude:** A. Thứ tự tất định và có lý do nghiệp vụ.
- **Quyết định:** A. Xếp hạng theo điểm giảm dần, rồi số flag giảm dần, rồi `submitted_amount` giảm dần, rồi `claim_id` tăng dần. Lý do: thứ tự tất định và có lý do nghiệp vụ (claim nhiều tiền hơn được ưu tiên điều tra).

### S6. Một rule kích hoạt nhiều lần trên cùng một claim

- **Vấn đề:** Một claim có thể chứa 2 bundle, 2 procedure lạc chẩn đoán, hoặc khớp duplicate với nhiều bản. Nếu cộng điểm theo số lần thì điểm bị thổi phồng.
- **Phương án:**
  - A. Mỗi rule tối đa một flag và một lần cộng điểm cho mỗi claim. Evidence liệt kê mọi lần khớp. *Đánh đổi:* điểm ổn định, đúng ý "weighted sum of triggered rules".
  - B. Cộng theo số lần. *Đánh đổi:* phản ánh mức độ, nhưng phá vỡ thang trọng số.
- **Đề xuất của Claude:** A
- **Quyết định:** A. Mỗi rule tối đa một flag và một lần cộng điểm cho mỗi claim; evidence liệt kê mọi lần khớp. Lý do: điểm ổn định, đúng ý 'weighted sum of triggered rules'.

### S7. Kiểu và cách làm tròn `risk_score`

- **Vấn đề:** Ví dụ trong đề dùng số nguyên (78). Cách làm tròn ảnh hưởng ngưỡng và test.
- **Phương án:**
  - A. Số nguyên, làm tròn half-up. *Đánh đổi:* khớp ví dụ.
  - B. Số thực 1 chữ số thập phân. *Đánh đổi:* mịn hơn, lệch ví dụ.
- **Đề xuất của Claude:** A. So sánh ngưỡng dùng điểm đã làm tròn để tránh lệch giữa báo cáo và output.
- **Quyết định:** A. `risk_score` là số nguyên, làm tròn half-up; so sánh ngưỡng dùng điểm đã làm tròn để báo cáo và output không lệch nhau. Lý do: khớp ví dụ của đề.

---

## D. Đo lường metric

### M1. ⚠️ Định nghĩa "false positive rate"

- **Vấn đề:** FPR theo chuẩn là FP/(FP+TN). Với 1.800 claim hợp lệ, cho phép tới 360 FP, rất lỏng. Nhiều người lại dùng "FPR" để chỉ 1 − precision (false discovery rate), khi đó chỉ cho phép khoảng 50 FP nếu bắt được khoảng 200. Hai cách hiểu chênh nhau hơn 7 lần về độ khó.
- **Phương án:**
  - A. Dùng FPR chuẩn làm chỉ số cam kết, đồng thời báo cáo precision và FDR. *Đánh đổi:* đúng thuật ngữ, minh bạch.
  - B. Dùng FDR làm chỉ số cam kết. *Đánh đổi:* thận trọng hơn, nhưng khó đạt nếu có hard negatives.
  - C. Đặt mục tiêu đạt cả hai. *Đánh đổi:* an toàn với mọi cách hiểu của người chấm, nhưng phải thiết kế kỹ.
- **Đề xuất của Claude:** A trong báo cáo, nhưng tự đặt mục tiêu ngầm đạt cả FDR ≤ 20%. Ghi rõ định nghĩa và công thức để người chấm không hiểu sai.
- **Quyết định:** A. FPR = FP / số claim hợp lệ là chỉ số chính (định nghĩa thống kê chuẩn); báo kèm FP / số claim bị flag (và precision), vì đề không nói rõ và cách thứ hai có thể vượt 20%.

### M2. Đơn vị đánh giá và mức chi tiết

- **Vấn đề:** Metric tổng hợp che mất pattern yếu. Ví dụ recall 90% có thể đi kèm upcoding chỉ đạt 40%. Đề chỉ yêu cầu precision/recall tổng.
- **Phương án:**
  - A. Ở cấp claim: confusion matrix, precision, recall, FPR, F1. Thêm recall theo từng pattern, precision theo từng rule, và FP tự nhiên so với hard negatives. *Đánh đổi:* thêm chút code báo cáo.
  - B. Chỉ số tổng. *Đánh đổi:* đủ yêu cầu tối thiểu.
- **Đề xuất của Claude:** A. Chính bảng chi tiết này thuyết phục người chấm là rule đúng chứ không phải trùng hợp.
- **Quyết định:** A. Ở cấp claim: confusion matrix, precision, recall, FPR, F1; thêm recall theo từng pattern, precision theo từng rule, và báo cáo riêng báo oan tự nhiên so với hard negatives. Lý do: metric tổng hợp che mất pattern yếu.

### M3. Tránh overfit metric trên một seed

- **Vấn đề:** Cùng một người viết generator, rule và tinh chỉnh trọng số/ngưỡng trên cùng một dataset thì metric gần như chắc chắn đẹp, nhưng không chứng minh được gì.
- **Phương án:**
  - A. Tinh chỉnh trên seed chính, báo cáo thêm trung bình ± độ lệch chuẩn trên 5 seed khác. *Đánh đổi:* thêm vài giây chạy và một bảng báo cáo.
  - B. Chỉ báo cáo seed chính. *Đánh đổi:* đơn giản.
- **Đề xuất của Claude:** A
- **Quyết định:** A. Tinh chỉnh trên dataset tuning, báo cáo trên dataset held-out (seed khác).

---

## E. Output và định dạng

### O1. File scored output

- **Vấn đề:** Đề yêu cầu "ranked list" và "scored output" nhưng không nói gồm mọi claim hay chỉ claim bị flag, cũng không nói định dạng.
- **Phương án:**
  - A. `scored_claims.json`: mảng JSON gồm cả 2.000 claim đã xếp hạng (claim 0 điểm có `flags: []`). *Đánh đổi:* đầy đủ, dễ đối chiếu.
  - B. Chỉ claim bị flag. *Đánh đổi:* gọn, nhưng khó chứng minh claim sạch đã được chấm.
  - C. JSON Lines. *Đánh đổi:* hợp streaming, nhưng ít thân thiện khi mở xem.
- **Đề xuất của Claude:** A. File khoảng 1 MB, vẫn dễ xem trên GitHub.
- **Quyết định:** A. `scored_claims.json`: mảng JSON gồm cả 2.000 claim đã xếp hạng (claim 0 điểm có `flags: []`). Lý do: đầy đủ, dễ đối chiếu, chứng minh claim sạch cũng đã được chấm.

### O2. Có mở rộng schema output không?

- **Vấn đề:** Schema trong đề gồm `claim_id`, `risk_score` và `flags[rule, severity, evidence]`. Thêm `rank` hay `details` (số liệu có cấu trúc) sẽ tiện cho phân tích, nhưng lệch mẫu.
- **Phương án:**
  - A. Giữ đúng schema của đề. *Đánh đổi:* an toàn khi chấm.
  - B. Thêm `rank` và `details` (map số liệu) vào mỗi flag. *Đánh đổi:* giàu thông tin, nhưng lệch mẫu.
- **Đề xuất của Claude:** A. Số liệu chi tiết nằm sẵn trong `evidence` và trong báo cáo metric.
- **Quyết định:** A. Giữ đúng schema của đề (`claim_id`, `risk_score`, `flags[rule, severity, evidence]`). Lý do: an toàn khi chấm; số liệu chi tiết nằm sẵn trong `evidence` và báo cáo metric.

### O3. Chuẩn viết evidence

- **Vấn đề:** Tiêu chí chấm nhấn mạnh evidence phải "specific, actionable". Cần chốt ngôn ngữ, định dạng số, và có trích `claim_id` liên quan không.
- **Phương án:**
  - A. Tiếng Anh như ví dụ, định dạng số `Locale.US` ("85,000"). Mỗi rule có một template cố định, gồm giá trị thực tế, ngưỡng hoặc giá trị tham chiếu, và claim liên quan. Ví dụ: `"Duplicate of CLM-00042: same member M-0101, provider P-07, date 2024-03-14, diagnosis J20.9"`. *Đánh đổi:* nhất quán, dễ test.
  - B. Tiếng Việt. *Đánh đổi:* lệch ví dụ của đề.
- **Đề xuất của Claude:** A
- **Quyết định:** A. Evidence bằng tiếng Anh như ví dụ của đề, định dạng số `Locale.US` (ví dụ 85,000); mỗi rule một template cố định gồm giá trị thực tế, giá trị tham chiếu hoặc ngưỡng, và claim liên quan. Lý do: nhất quán với ví dụ của đề, dễ test.

### O4. Báo cáo metric

- **Vấn đề:** Đề yêu cầu nộp "metrics report" mà không nói định dạng.
- **Phương án:**
  - A. `metrics_report.md` cho người đọc, cộng `metrics.json` cho máy. Nội dung gồm định nghĩa, confusion matrix, các bảng của M2/S3/M3, thời gian xử lý và các giả định. *Đánh đổi:* đầy đủ nhất.
  - B. Chỉ in ra console. *Đánh đổi:* không đáp ứng yêu cầu nộp.
  - C. Chỉ markdown. *Đánh đổi:* đủ dùng, nhưng khó so sánh tự động.
- **Đề xuất của Claude:** A
- **Quyết định:** A. `metrics_report.md` cho người đọc, cộng `metrics.json` cho máy; nội dung gồm định nghĩa, confusion matrix, các bảng của M2, S3, M3, thời gian xử lý và các giả định.

### O5. Tên định danh rule

- **Vấn đề:** Ví dụ trong đề dùng `upcoding` và `amount_clustering` (snake_case). Cần chốt đủ 8 tên vì chúng dùng chung cho output, config và báo cáo.
- **Phương án:**
  - A. `duplicate_claim`, `rapid_resubmission`, `upcoding`, `unbundling`, `phantom_billing`, `weekend_anomaly`, `diagnosis_procedure_mismatch`, `amount_clustering`. *Đánh đổi:* đồng bộ với ví dụ của đề.
  - B. Tên ngắn (`duplicate`, `mismatch`…). *Đánh đổi:* gọn, kém rõ nghĩa.
- **Đề xuất của Claude:** A
- **Quyết định:** A. `duplicate_claim`, `rapid_resubmission`, `upcoding`, `unbundling`, `phantom_billing`, `weekend_anomaly`, `diagnosis_procedure_mismatch`, `amount_clustering`. Lý do: đồng bộ với ví dụ của đề và với tên trong bảng trọng số.

---

## F. Kiến trúc, hiệu năng và test

### T1. Kiến trúc engine

- **Vấn đề:** Năm trong tám rule cần thống kê trên toàn dataset (duplicate, rapid, upcoding, phantom, weekend). Nếu mỗi rule tự quét so cặp thì độ phức tạp O(n²) và code lặp.
- **Phương án:**
  - A. Hai pha. Pha 1 xây `DatasetIndex` một lần (nhóm theo member + chẩn đoán, provider + ngày, thống kê procedure, tỉ lệ cuối tuần). Pha 2 chạy `Rule.evaluate(claim, index) → Optional<Flag>`. *Đánh đổi:* O(n), dễ test từng rule bằng index giả.
  - B. Mỗi rule tự xử lý cả lô và trả về `Map<claimId, Flag>`. *Đánh đổi:* các rule độc lập với nhau, nhưng tính lặp lại và test cồng kềnh hơn.
- **Đề xuất của Claude:** A. Dùng record, có thể dùng sealed interface `Rule` của Java 21. Mỗi rule là một class nhỏ.
- **Quyết định:** A. Hai pha: pha 1 xây `DatasetIndex` một lần (nhóm theo member + chẩn đoán, provider + ngày, thống kê procedure, tỉ lệ cuối tuần), pha 2 chạy `Rule.evaluate(claim, index)`. Lý do: O(n), dễ test từng rule bằng index giả; mỗi rule là một class nhỏ.

### T2. CLI và đóng gói

- **Vấn đề:** Người chấm cần chạy lại được bằng một lệnh. Đề không nói cách chạy.
- **Phương án:**
  - A. Một `Main` có subcommand `generate | score | evaluate | all`, đóng gói fat jar bằng `maven-shade-plugin`. *Đánh đổi:* chạy được bằng `java -jar`.
  - B. `exec-maven-plugin`. *Đánh đổi:* không cần fat jar, nhưng lệnh dài và phụ thuộc Maven khi chạy.
- **Đề xuất của Claude:** A. Tự parse tham số, không thêm thư viện CLI để giữ đúng stack.
- **Quyết định:** A. Một `Main` có subcommand `generate | score | evaluate | all`, đóng gói fat jar bằng `maven-shade-plugin`; tự parse tham số, không thêm thư viện CLI. Lý do: người chấm chạy lại được bằng `java -jar`.

### T3. Kiểu dữ liệu tiền

- **Vấn đề:** `double` có sai số khi so sánh biên (49.999,999…). `BigDecimal` an toàn nhưng rườm rà khi tính thống kê.
- **Phương án:**
  - A. `long` (đơn vị nguyên, nếu chọn D4-A). Riêng mean/SD tính bằng `double`. *Đánh đổi:* so sánh biên chính xác, code gọn.
  - B. `BigDecimal` toàn bộ. *Đánh đổi:* chuẩn tài chính, code dài hơn.
- **Đề xuất của Claude:** A, nhưng phụ thuộc quyết định D4.
- **Quyết định:** A. Dùng `long` cho số tiền (khớp D4-A), mean/SD tính bằng `double`. Lý do: so sánh biên chính xác, code gọn.

### T4. Đo hiệu năng (< 30 giây)

- **Vấn đề:** 2.000 claim với thuật toán O(n) chạy dưới 1 giây, nên ngưỡng 30 giây gần như chắc chắn đạt. Điều cần chốt là đo cái gì (có tính bước sinh dữ liệu và khởi động JVM không) và chứng minh ra sao.
- **Phương án:**
  - A. Đo đoạn load → score → ghi output bằng `System.nanoTime`, ghi vào báo cáo. Thêm một test chịu tải 100.000 claim để chứng minh khả năng mở rộng. *Đánh đổi:* thuyết phục, thêm khoảng 1 test.
  - B. Chỉ một test JUnit có `assertTimeout(30s)` trên 2.000 claim. *Đánh đổi:* đủ yêu cầu tối thiểu.
- **Đề xuất của Claude:** A, kèm cả test của B.
- **Quyết định:** A. Đo đoạn load, score, ghi output bằng `System.nanoTime`, ghi vào báo cáo; thêm một test chịu tải 100.000 claim và một test JUnit `assertTimeout(30s)` trên 2.000 claim.

### T5. Chiến lược test

- **Vấn đề:** Đề yêu cầu tối thiểu 15 unit test bao phủ từng rule và edge case. Cần chốt có kiểm tra mục tiêu metric bằng test không: test kiểu này dễ chập chờn và dễ bị coi là "test cho đẹp".
- **Phương án:**
  - A. Khoảng 25–30 test:
    - Mỗi rule ít nhất 2 test (dương tính và biên âm tính theo R10).
    - Test chuẩn hoá điểm, phá hoà, metric tính đúng trên confusion matrix nhỏ.
    - Generator: số lượng từng pattern, tất định theo seed.
    - Validate đầu vào (R11).
    - Một test tích hợp trên seed cố định kiểm tra recall ≥ 70% và FPR ≤ 20%.

    *Đánh đổi:* bao quát, thêm khoảng 1 giờ.
  - B. Đúng 15–16 test, mỗi rule 2 test. *Đánh đổi:* vừa đủ yêu cầu.
- **Đề xuất của Claude:** A. Fixture dựng bằng builder nhỏ trong code test, không phụ thuộc file CSV lớn.
- **Quyết định:** A. Khoảng 25-30 test: mỗi rule ít nhất 2 test (dương tính và biên âm tính theo R10); test chuẩn hoá điểm, phá hoà, metric tính đúng trên confusion matrix nhỏ; test generator (số lượng từng pattern, tất định theo seed); test validate đầu vào (R11); một test tích hợp trên seed cố định kiểm tra recall ≥ 70% và FPR ≤ 20%. Fixture dựng bằng builder nhỏ trong code test. Đề yêu cầu tối thiểu 15 test.

### T6. Thư viện phụ thuộc

- **Vấn đề:** Stack đã chốt là Java 21, Maven, JUnit 5, Jackson. Cần xác định có được dùng module Jackson phụ trợ và thư viện test bổ sung không.
- **Phương án:**
  - A. `jackson-databind`, `jackson-datatype-jsr310` (cho `LocalDate`), `jackson-dataformat-csv`, JUnit 5. Không có thư viện nào khác. *Đánh đổi:* vẫn nằm trong họ Jackson, đủ dùng.
  - B. Thêm AssertJ và Lombok. *Đánh đổi:* code gọn hơn, nhưng ra ngoài stack đã chốt (record của Java 21 đã thay được Lombok).
- **Đề xuất của Claude:** A
- **Quyết định:** A. `jackson-databind`, `jackson-datatype-jsr310` (cho `LocalDate`), `jackson-dataformat-csv`, JUnit 5; không thêm thư viện nào khác (record của Java 21 thay được Lombok).

### T7. Ước lượng timeline (đề yêu cầu)

- **Vấn đề:** Phần Note yêu cầu nêu ước lượng timeline. Đề ghi độ khó 4–6 giờ.
- **Phương án:**
  - A. Khoảng 6 giờ:
    - thiết kế 0,5 giờ;
    - generator 1,5 giờ;
    - rule cùng index 1,5 giờ;
    - chấm điểm, output, metric 1 giờ;
    - test 1 giờ;
    - README và báo cáo 0,5 giờ.

    *Đánh đổi:* khớp độ khó của đề.
  - B. Khoảng 8 giờ, có hard negatives, nhiều seed và test chịu tải. *Đánh đổi:* chất lượng cao hơn, vượt khung đề gợi ý.
- **Đề xuất của Claude:** A cộng 1 giờ dự phòng nếu chọn D13 và M3.
- **Quyết định:** A cộng 1 giờ dự phòng cho D13 và M3, tổng khoảng 7 giờ: thiết kế 0,5; generator 1,5; rule cùng index 1,5; chấm điểm, output, metric 1; test 1; README và báo cáo 0,5; dự phòng 1. Lịch làm: 25-27/10, buffer sáng 28/10.

---

## G. Bảng trọng số 8 rule (thang 1–5)

| # | Rule | Trọng số đề xuất | Lý do ngắn (cho trọng số đề xuất) | Trọng số |
|---|------|:---:|------------|:---:|
| 1 | `duplicate_claim` | 5 | Khớp chính xác 4 khoá, gần như chắc chắn là gian lận hoặc lỗi trả trùng. Thiệt hại bằng 100% số tiền claim | 5 |
| 2 | `rapid_resubmission` | 3 | Tín hiệu khá rõ, nhưng tái khám hợp lệ trong 7 ngày (bệnh mạn tính, theo dõi sau điều trị) khá phổ biến | 2 |
| 3 | `upcoding` | 4 | Tín hiệu thống kê mạnh, tác động tài chính lớn. Có khoảng 2% FP tự nhiên ở đuôi phân phối nên không đặt 5 | 4 |
| 4 | `unbundling` | 4 | Tất định từ bảng mã, thể hiện thao túng có chủ đích. Đôi khi là lỗi mã hoá không cố ý | 4 |
| 5 | `phantom_billing` | 4 | Lưu lượng phi lý về mặt vật lý, nhưng là tín hiệu cấp provider-ngày nên không tách được claim hợp lệ trong ngày đó | 5 |
| 6 | `weekend_anomaly` | 2 | Ca cấp cứu cuối tuần là có thật. Tín hiệu yếu khi đứng một mình, chủ yếu có giá trị khi kết hợp với rule khác | 3 |
| 7 | `diagnosis_procedure_mismatch` | 3 | Phụ thuộc độ đầy đủ của whitelist. Lỗi mã hoá hành chính thường gặp và không phải lúc nào cũng là gian lận | 3 |
| 8 | `amount_clustering` | 2 | Nhiều claim hợp lệ tự nhiên rơi gần ngưỡng. Đứng một mình thì yếu, đi kèm rule khác thì mạnh. Khớp severity 2 trong ví dụ của đề | 2 |

> **Lưu ý (theo S1-A, tổng trọng số 28):** Điểm của một rule đơn lẻ là round(100 × w / 28): w = 2 cho 7, w = 3 cho 11, w = 4 cho 14, w = 5 cho 18. Trọng số tác động lên metric thông qua ngưỡng T (S3). Nếu T lớn hơn điểm của một rule đơn lẻ, cả pattern chỉ kích hoạt rule đó bị loại khỏi recall. Nếu T ≤ 7 thì mọi claim có ít nhất một flag đều bị gắn cờ, và trọng số chỉ còn ảnh hưởng thứ hạng, không ảnh hưởng claim nào bị gắn cờ.

### Lý do trọng số

- **duplicate_claim = 5:** khớp chính xác bốn trường, hầu như không có lý do hợp lệ; báo oan chủ yếu do lỗi hệ thống nộp hai lần.
- **phantom_billing = 5:** hơn 30 claim một ngày ở một provider là khối lượng gần như không thể thực hiện; bằng chứng ở mức provider nên mạnh. Lưu ý trong writeup: luật cũng flag claim thường cùng ngày (D10), làm precision ở mức claim giảm.
- **upcoding = 4:** bằng chứng thống kê mạnh, nhưng ca nặng thật cũng đắt hơn trung bình, và độ tin cậy phụ thuộc cách tính mean/std (R4).
- **unbundling = 4:** bằng chứng cấu trúc (tách lẻ trong khi có mã gói), nhưng phụ thuộc bảng bundle do mình tự thiết kế và quy ước thanh toán từng nơi.
- **weekend_anomaly = 3:** có thể là ca cấp cứu thật; bằng chứng gián tiếp, cần đủ hai điều kiện (phẫu thuật, provider ít làm cuối tuần).
- **diagnosis_procedure_mismatch = 3:** về mặt y khoa là dấu hiệu tốt, nhưng bảng ánh xạ có thể chưa đầy đủ và mã chẩn đoán có thể gán chưa chuẩn.
- **rapid_resubmission = 2:** tái khám hoặc lấy thuốc trong 7 ngày rất phổ biến, nhất là bệnh mãn tính hoặc biến chứng, nên nhiều ca hợp lệ vô tình trùng luật.
- **amount_clustering = 2:** nhiều claim hợp lệ tình cờ nằm gần ngưỡng; chỉ là dấu hiệu hành vi, nên cần kết hợp luật khác.

---

## H. Rủi ro khả thi

### H1. Mục tiêu recall ≥ 70% và FPR ≤ 20% có khả thi không?

**Có, gần như chắc chắn**, vì cùng một người viết cả generator lẫn rule: mọi pattern gian lận được cài đặt đúng theo định nghĩa của rule. Ước lượng thô với các đề xuất ở trên:

| Thành phần | Ước lượng |
|------------|-----------|
| Recall | 90–100%. Rủi ro nằm ở upcoding (masking, R4), weekend (tỉ lệ ≥ 5%, D11), phantom (D10) và ngưỡng T loại pattern nhẹ (S3) |
| FP tự nhiên của upcoding | Khoảng 40 (2,3% của 1.800 nếu nhiễu chuẩn, D8) |
| FP của amount clustering | Khoảng 5–15, tuỳ phân phối giá INPATIENT |
| FP của rapid re-submission | Khoảng 5–10 tự nhiên, cộng phần cài thêm theo D13-C |
| FP của mismatch và weekend | Khoảng 0 nếu dữ liệu hợp lệ "sạch", tăng thêm nếu cài nhiễu thực tế (D13) |
| FPR chuẩn | Khoảng 3–7%, đạt rất dễ |
| Precision | Khoảng 70–85%. Đây mới là chỉ số dễ trượt nếu người chấm hiểu "FPR" là 1 − precision (M1) |

**Nơi có thể trượt thật:**

1. Upcoding tính mean/SD kể cả claim đang xét trên nhóm nhỏ: z bị chặn bởi (n−1)/√n, nên recall upcoding tụt mạnh.
2. Dồn claim gian lận cuối tuần vào ít provider: tỉ lệ cuối tuần vượt 5%, recall weekend có thể về 0.
3. Phantom theo D10-C: recall phantom gần 0, tức mất khoảng 12% recall tổng.
4. Ngưỡng T quá cao so với trọng số 2: mất tới 20% recall.
5. Hiểu FPR là FDR, cộng thêm hard negatives: precision có thể chạm sát 80%.

### H2. Những điểm có thể làm metric đẹp giả

| # | Bẫy | Biểu hiện | Cách phòng |
|---|-----|-----------|------------|
| 1 | Generator dùng chung hằng số ngưỡng với rule | Phát hiện mang tính hiển nhiên (tautology), ví dụ cố định số tiền 48.000 cho mọi claim clustering | Tham số generator độc lập. Giá trị gian lận trải rộng (z từ 2,2 đến 6, số tiền rải đều trong dải) |
| 2 | Dữ liệu hợp lệ quá sạch | Nhiễu đều, chỉ dùng whitelist, không có tái khám, không có số tiền gần ngưỡng: FP gần 0 | Nhiễu chuẩn (D8), hard negatives và nhiễu thực tế (D13) |
| 3 | Gán nhãn gian lận cho claim liên quan | Bản gốc duplicate hoặc cả ngày phantom được gán nhãn để biến FP thành TP | D9-A. Mọi sai lệch số đếm (D10) đều ghi rõ |
| 4 | Chọn định nghĩa FPR lỏng | Báo cáo "FPR 3%" trong khi precision chỉ 60% | Luôn báo cáo kèm precision và FDR (M1) |
| 5 | Tinh chỉnh và báo cáo trên cùng một seed | Trọng số và ngưỡng khớp riêng một dataset | Báo cáo đa seed (M3) |
| 6 | Mỗi claim gian lận chỉ khớp sạch một pattern | Gian lận thật thường tinh vi và chồng lấn | Nêu rõ trong giới hạn của báo cáo. Có thể thêm vài claim "đa pattern" |
| 7 | Dùng thống kê cả năm (dữ liệu tương lai) | Lợi thế không có khi chạy thực tế | Ghi giả định (R12) |
| 8 | Engine đọc nhầm cột nhãn | Rò rỉ nhãn | File ground truth riêng, engine không truy cập (D3) |
| 9 | Recall tổng che pattern yếu | "Recall 92%" nhưng upcoding chỉ 40% | Recall theo từng pattern (M2) |
| 10 | Upcode với hệ số quá lớn | ×5 giá trung bình nên ai cũng bắt được | Hệ số đa dạng, có ca sát ngưỡng (z khoảng 2,1–2,5) |

### H3. Đề xuất mục tiêu nội bộ (cao hơn mục tiêu của đề)

- Recall ≥ 85% tổng và ≥ 70% cho **từng** pattern.
- FPR chuẩn ≤ 5%.
- Precision ≥ 75%, tính cả khi có hard negatives.
- Độ lệch chuẩn của recall và precision giữa các seed ≤ 5 điểm phần trăm.

- **Quyết định (mục tiêu nội bộ):** Nhận: recall ≥ 85% tổng và ≥ 70% cho từng pattern; FPR chuẩn ≤ 5%; độ lệch chuẩn của recall và precision giữa các seed ≤ 5 điểm phần trăm. Precision ≥ 75% chỉ báo cáo, không coi là mục tiêu (Q17).

---

## Phụ lục — Quyết định bổ sung Q1–Q24

> Chốt ngày **2026-10-25**, sau vòng rà soát mâu thuẫn trước khi lập kế hoạch triển khai.
> Khi phụ lục khác với phần thân, **phụ lục được ưu tiên**. Cột "Quan hệ" ghi rõ mục nào bị ghi đè (thay một phần quyết định cũ) hay chỉ được bổ sung (điền chỗ còn trống, không đổi quyết định cũ).

### Mâu thuẫn giữa các quyết định

- **Q1** — Quan hệ: *ghi đè* R3 (thay cả quyết định "B kết hợp C"). Chốt ngày **2026-10-25**.
  - Quyết định: **A**. Rule upcoding chỉ xét claim có **đúng một procedure**; claim nhiều procedure bị bỏ qua và được đếm trong báo cáo.
  - Nhóm so sánh là các claim chỉ có cùng một procedure đó. Nhóm dưới `n_min` thì không flag và được đếm là bị bỏ qua (Q11).
  - Lý do: Chỉ khi claim có một procedure thì số tiền mới so sánh được với giá của đúng procedure đó. Claim nhiều procedure đã được rule unbundling phụ trách, nên bỏ qua ở rule này để mỗi claim gian lận chỉ khớp một pattern.
- **Q2** — Quan hệ: *bổ sung* R4, D8, H2#10.
  - Generator sinh số tiền upcoding = giá gốc × U(1,5; 2,5), độc lập với engine.
  - Chấp nhận masking của R4-A, báo cáo trung thực recall riêng của upcoding.
  - Quyết định đổi sang median/MAD chỉ được đưa ra trên dataset tuning, trước khi đóng băng (Task 17). Mã "T8" trong R4 đã sửa thành "Task 17".
- **Q3** — Quan hệ: *bổ sung* D5, R9; *ghi đè* số lượng ở R9 và D13 (20–40 thành đúng 20).
  - Danh mục có 2–3 procedure nội trú với μ ∈ [38.000; 45.000] để claim clustering gian lận không đồng thời bị upcoding bắt. Các procedure này không được chiếm tỉ lệ lớn trong tổng số claim.
  - Cố định **20** claim hợp lệ cài trong dải 47.500–49.999.
  - Báo cáo tách hai nhóm "rơi vào dải tự nhiên" và "cài có chủ đích".
- **Q4** — Quan hệ: *bổ sung* D12 (cách kiểm tra chồng lấn).
  - Generator tránh chồng lấn ngay từ cách sinh: không gọi rule của engine, không sinh lại, không lọc.
  - Một test chạy engine trên dữ liệu đã sinh và chỉ báo cáo số claim gian lận khớp từ 2 rule trở lên.
- **Q5** — Quan hệ: *bổ sung* S3, mục G, H3. Chốt ngày **2026-10-25**.
  - Giữ tiêu chí chọn T của S3: recall cao nhất với FPR chuẩn ≤ 20% (đúng chữ của đề). FDR chỉ để báo cáo.
  - Quét T = 1..100. Trong các T đạt FPR ≤ 20%, chọn T có TP cao nhất; nếu hoà thì FP thấp nhất; nếu vẫn hoà thì T lớn nhất.
  - Trọng số giữ nguyên như mục G; chỉ T được chọn từ dữ liệu.
  - Hệ quả: với S1-A, điểm khác 0 thấp nhất là 7, nên nếu T ≤ 7 thì trọng số chỉ ảnh hưởng thứ hạng (L2). Lý do giữ tiêu chí này sau khi có số liệu tuning: xem quyết định Task 17 ở S3.
  - Ghi chú "Lưu ý" ở mục G đã viết lại theo S1-A; giới hạn tương ứng ghi ở `docs/limitations.md` (L2).
- **Q6** — Quan hệ: *bổ sung* D1, M3 (điền số seed). Seed tuning = 42, held-out = 2024, cộng 5 seed ổn định 101–105 chạy với config đã đóng băng.
- **Q7** — Quan hệ: *bổ sung* D3 (giá trị nhãn); *ghi đè* ngân sách D13 (30–50 thành khoảng 25, cộng kịch bản riêng).
  - `fraud_pattern` ∈ {8 tên rule theo O5, `none`, `legit_near_miss`, `legit_noise`}.
  - `legit_near_miss`: hợp lệ, sát biên, không kích hoạt rule. `legit_noise`: hợp lệ nhưng cài có chủ đích để kích hoạt rule (20 claim của R9, 6 claim cùng ngày phantom).
  - Kịch bản "một provider có đúng 30 claim trong một ngày" tính riêng, ngoài ngân sách. Các near-miss khác khoảng 25 claim.
- **Q8** — Quan hệ: *ghi đè* D10 ("ít nhất 6" thành "đúng 6"). Đúng 6 claim hợp lệ cùng ngày phantom; ngày phantom là ngày thường, tại một phòng khám tầm trung không thuộc nhóm "chỉ làm ngày thường".
- **Q9** — Quan hệ: *ghi đè* D5, S4 (đã sửa trực tiếp trong thân). Danh mục tách thành `procedures.json`, `diagnoses.json`, `bundles.json`, `diagnosis-procedures.json`; tham số engine ở `fraud-config.json`.
- **Q10** — Quan hệ: *ghi đè* T2 (thêm subcommand).
  - Thêm subcommand `tune`, ghi `config/frozen-config.json`.
  - File này phải được commit trước khi sinh held-out.
  - Lệnh `all` chỉ dùng config đã đóng băng.

### Chỗ còn trống

- **Q11** — Quan hệ: *bổ sung* R3, R4. `n_min = 10`, SD mẫu (chia n−1), SD = 0 thì không flag.
- **Q12** — Quan hệ: *bổ sung* R7. Lịch sử tối thiểu 20 claim (đã loại claim đang xét); claim có ít nhất 1 procedure phẫu thuật là đủ để xét.
- **Q13** — Quan hệ: *bổ sung* D11, D6. 8 provider "chỉ làm ngày thường", mỗi provider ít nhất 60 claim, không có claim hợp lệ nào vào cuối tuần, mỗi provider 2–3 claim gian lận cuối tuần.
- **Q14** — Quan hệ: *bổ sung* R8. Bất kỳ procedure nào không hợp lệ với chẩn đoán thì flag. Có 1–2 mã chung (phí khám) hợp lệ với mọi chẩn đoán.
- **Q15** — Quan hệ: *bổ sung* generator (D9–D12).
  - Duplicate: copy y nguyên procedure và số tiền.
  - Rapid: cách 1–7 ngày, khoảng 70% cùng provider.
  - Clustering: số tiền rải đều trong 47.500–49.999.
  - Mismatch: thay một procedure bằng procedure cùng `claim_type` nhưng không hợp lệ với chẩn đoán.
- **Q16** — Quan hệ: *bổ sung* O1, D1. Nộp cả `data/tuning/` và `data/heldout/`; số liệu chính trong README lấy từ held-out.
- **Q17** — Quan hệ: *ghi đè* H3 (bỏ một mục tiêu). Precision ≥ 75% chỉ báo cáo, không coi là mục tiêu.
- **Q18** — Quan hệ: *bổ sung* T5. Test tích hợp chạy trên seed held-out cố định, viết trước khi chạy held-out. Nếu fail thì giữ nguyên test, báo thật, rồi hỏi lại.
- **Q19** — Quan hệ: *bổ sung* T4. Test 100.000 claim dùng builder sinh claim hợp lệ ngẫu nhiên, không dùng generator; giới hạn < 30 giây và in thời gian ra.
- **Q20** — Quan hệ: *bổ sung* T1, T2. `pom.xml` ở `AI_Engineering_Challenges/`, package `com.papaya.fraud`, artifact `fraud-engine`.
- **Q21** — Quan hệ: quy trình làm việc (không thuộc mục thiết kế nào).
  - Repo git đặt ở gốc `papaya-insurtech/` (chứa `Logical_Questions/` và `AI_Engineering_Challenges/`). Repo con rỗng trong `AI_Engineering_Challenges/` đã bị bỏ.
  - Làm thẳng trên nhánh chính `main`, mỗi task một commit. Câu trả lời ban đầu là làm trên `feat/challenge-10`; đã đổi theo chỉ đạo ngày 2026-10-25.
  - Log AI: giữ nguyên văn các file `.txt` từ `/export` trong `docs/ai-log/`, không chuyển đổi định dạng. Claude nhắc trước mỗi lần `/clear` hoặc `/compact`.

### Chỗ lệch giữa đề gốc và quyết định

- **Q22** — Quan hệ: *bổ sung* S1. Chấp nhận lệch ví dụ của đề: với S1-A, hai flag (4 + 2) cho round(600 / 28) = 21 điểm thay vì 78. Writeup giải thích lý do chọn S1-A.
- **Q23** — Quan hệ: *bổ sung* R2, R3, R7, R8. Các diễn giải thêm so với chữ của đề được đưa thành mục "Interpretations" trong writeup.
- **Q24** — Quan hệ: *bổ sung* M3. Held-out chỉ chống overfit theo seed, không chống overfit theo generator; đã ghi ở `docs/limitations.md` (L1).

### Quyết định của generator spec (G1–G14)

> Chốt ngày **2026-10-25**. Chi tiết đầy đủ ở `docs/generator-spec.md`, mục 9.

- **D3 (ghi đè, theo G5):** `ground_truth.csv` có **5 cột**: `claim_id, is_fraud, fraud_pattern, related_claim_id, scenario`. Cột `scenario` ghi mã kịch bản chi tiết (ví dụ `phantom_day`, `day_30_claims`, `near_miss_rapid_8_days`), để báo cáo near-miss và noise theo từng loại (M2). `claims.csv` vẫn đúng 10 cột; engine vẫn không đọc file ground truth.
- **G1** — Quan hệ: *bổ sung* D1. Mỗi giai đoạn sinh có một `SplittableRandom` riêng, seed = `mix64(seed XOR fnv1a64(tên giai đoạn))` (hàm trộn SplitMix64). Không dùng công thức tuyến tính `seed × 31 + mã`.
- **G2** — Quan hệ: *bổ sung* D12, Q4. Khi một lựa chọn trong lúc sinh vi phạm ràng buộc **của generator**, generator rút lại lựa chọn đó, tối đa 1.000 lần (quá thì ném lỗi). Việc rút lại không bao giờ dựa trên kết quả rule của engine; điểm này khác với "lọc lại sau khi sinh" mà Q4 cấm. Ghi rõ vào writeup.
- **G3–G14** — đồng ý theo đề xuất trong `generator-spec.md`. Bổ sung: upcoding chia 20 claim U(1,5; 2,5) và 10 claim U(1,2; 1,6); 6 near-miss quanh dải clustering nằm trong quota 32 claim tự nhiên của 4 nhóm.
