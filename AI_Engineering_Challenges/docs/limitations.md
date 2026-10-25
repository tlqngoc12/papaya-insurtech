# Limitations

Các hạn chế được ghi lại ngay khi gặp trong quá trình làm. Cuối cùng sẽ đưa vào writeup.
Mỗi mục ghi nguồn (mã quyết định trong `design-decisions.md` hoặc task) để truy ngược.

## L1. Held-out chỉ chống overfit theo seed, không chống overfit theo generator

*Nguồn: Q24, M3.*

Dataset tuning (seed 42), held-out (seed 2024) và các seed ổn định (101–105) đều sinh từ cùng một generator do chính mình viết. Held-out bảo đảm T không được chọn cho vừa một seed cụ thể. Nhưng nó không chứng minh được rule hoạt động tốt trên gian lận thật, vì cả hai dataset cùng phân phối và các pattern gian lận được cài theo đúng định nghĩa của rule.

## L2. Với S1-A, ngưỡng T ≤ 7 làm trọng số không ảnh hưởng việc gắn cờ

*Nguồn: Q5, S1, S3.*

Với score = round(100 × Σw_triggered / Σw_all) và tổng trọng số 28, điểm khác 0 thấp nhất là 7 (một rule có trọng số 2). Nếu T được chọn ≤ 7 thì mọi claim có ít nhất một flag đều bị gắn cờ. Khi đó trọng số chỉ ảnh hưởng thứ hạng, không ảnh hưởng claim nào bị gắn cờ, và metric precision/recall/FPR không phụ thuộc trọng số.

## L3. Chấm điểm theo lô dùng thống kê cả năm

*Nguồn: R12.*

Upcoding, weekend anomaly và rapid re-submission dùng thống kê trên toàn dataset, nên một claim tháng 1 được đánh giá bằng cả dữ liệu tháng 12. Hệ thống chạy trước khi thanh toán sẽ không có dữ liệu tương lai. Hướng mở rộng: chỉ dùng dữ liệu trước claim đang xét.

## L4. Unbundling chỉ xét trong phạm vi một claim

*Nguồn: R5.*

Trường hợp tách các thành phần của một bundle ra nhiều claim cùng member, provider và ngày không bị phát hiện bởi rule unbundling.

## L5. Số tiền claim unbundled bị cắt dưới dải clustering

*Nguồn: Task 02, mục (c)2; D12, Q4.*

Để mỗi claim gian lận chỉ mang một pattern (D12), generator cắt nhiễu của claim unbundled sao cho số tiền không vượt 47.000, tức không bao giờ rơi vào dải clustering 47.500–49.999. Ví dụ, gói cholecystectomy có tổng thành phần 37.000, nên nhiễu +2σ có thể đẩy số tiền vào dải. Hệ quả: trong dữ liệu sinh ra, unbundling và amount clustering không bao giờ cùng xuất hiện trên một claim. Gian lận thật có thể kết hợp cả hai, và engine chưa được đo trên trường hợp đó.

## L6. Cờ `surgical` là định nghĩa riêng của bài

*Nguồn: Task 02, R7.*

Rule weekend anomaly dựa vào cờ `surgical` trong `procedures.json`. Cờ này do mình tự gán, không theo một chuẩn phân loại thủ thuật nào. Cụ thể, PCI (P-2011) và coronary angiography (P-2010) là thủ thuật can thiệp qua da nên được để `surgical = false`. Một hệ thống thật cần một nguồn phân loại thủ thuật chính thức, và kết quả weekend anomaly sẽ thay đổi theo định nghĩa đó.

## L7. Tính đúng lâm sàng của whitelist không được test tự động

*Nguồn: Task 02, thử phá test có chủ ý.*

Tính đúng lâm sàng của whitelist do tác giả review thủ công qua git diff; test chỉ bắt lỗi cấu trúc và claim_type. Ví dụ, nếu tráo một cặp đúng lấy một cặp sai về y khoa nhưng cùng claim_type, số cặp vẫn là 64 và không test nào đỏ.

## L8. Upcoding không xét claim nhiều procedure

*Nguồn: Q1, Task 07.*

Rule upcoding chỉ xét claim có đúng một procedure. Rule unbundling chỉ bắt claim chứa **đủ** các thành phần của một bundle. Vì vậy một claim nhiều procedure không chứa bundle nào (ví dụ P-2001 + P-2002 cho viêm phổi) mà bị khai số tiền quá cao thì không rule nào kiểm tra số tiền của nó. Số claim bị bỏ qua vì có nhiều procedure được đếm và ghi trong báo cáo metric.

## L9. Upcoding tính mean/SD có cả claim đang xét (masking)

*Nguồn: R4-A, Q2, Task 07.*

Vì mean và SD tính trên cả nhóm, kể cả claim đang xét, chính số tiền bất thường kéo mean lên và làm SD phình ra. Với nhóm n claim, z của một claim không bao giờ vượt (n − 1)/√n, dù số tiền lớn đến đâu: n = 5 cho tối đa 1,79, n = 10 cho tối đa 2,85. Nhóm càng nhỏ thì upcoding càng khó bị bắt, và nhiều claim upcoded trong cùng nhóm còn che lẫn nhau.

**Mở rộng (đo trên seed 42, Task 13–14, chỉ là số kiểm tra):**
- Masking tác động lên cả FP tự nhiên. Các claim upcoded làm phình SD của chính nhóm chúng, nên số claim hợp lệ bị upcoding flag giảm từ **34** (dữ liệu nền, chưa có claim upcoded) xuống **9** sau khi chèn 30 claim upcoded vào 21 nhóm. Ở các nhóm có claim upcoded, FP tự nhiên gần như về 0; ở các nhóm không có, FP giữ nguyên.
- Vì vậy con số khoảng 2,3% FP tự nhiên ở D8 chỉ đúng với nhóm "sạch", không có claim upcoded.
- Precision của upcoding đo được phụ thuộc vào việc nhóm nào chứa claim upcoded. Đây là đặc điểm của dữ liệu tổng hợp, không phải tính chất ổn định của rule.
- Masking cũng làm sót claim thật: CLM-01130 (P-2102, hệ số 1,38) có z = 1,58, nhưng nếu bỏ 2 claim upcoded khác cùng nhóm thì z = 2,88 và claim sẽ bị bắt.

## L10. Recall của các rule dựa trên bảng tham chiếu cao do cấu trúc

*Nguồn: Task 12, generator spec.*

Unbundling, diagnosis-procedure mismatch và duplicate so khớp trên bảng tham chiếu hoặc khóa cố định (bundle, whitelist, bốn trường của duplicate). Generator và engine dùng **chung** các bảng đó, nên mọi claim gian lận thuộc ba pattern này được tạo đúng theo định nghĩa mà engine kiểm tra. Recall gần 100% của ba rule này vì vậy là hệ quả cấu trúc, không phải bằng chứng rule tốt. Báo cáo metric sẽ **tách riêng** nhóm rule dựa trên bảng tham chiếu (duplicate, unbundling, mismatch) với nhóm rule thống kê hoặc ngưỡng (rapid, upcoding, phantom, weekend, clustering).

## L11. Bệnh viện "chỉ làm ngày thường" nhận cả ca cấp cứu vào ngày thường

*Nguồn: D11, Q13, Task 12.*

Tám bệnh viện "chỉ làm ngày thường" không có claim hợp lệ nào vào cuối tuần, kể cả các chẩn đoán cấp cứu như nhồi máu cơ tim (I21.9), đột quỵ (I63.9) hay sinh nở (O80, O82). Điều này không thực tế: cấp cứu không chọn ngày. Đây là giả định của đề ("providers who never operate on weekends"), được giữ để rule weekend anomaly có dữ liệu kiểm tra.

## L12. Claim gian lận có nhiễu số tiền hẹp hơn claim hợp lệ

*Nguồn: G7, generator spec mục 5.*

Để claim gian lận ngoài upcoding không vô tình vượt ngưỡng upcoding (D12), generator cắt nhiễu số tiền của chúng (và của bản gốc duplicate/rapid) ở m ≤ 1,25, thay vì ±3σ (m ≤ 1,45) như claim hợp lệ. Engine không dùng đặc điểm này, nhưng một mô hình học máy huấn luyện trên dataset này có thể học được nó.

## L13. Kết quả 100% của weekend anomaly dựa trên bằng chứng ít

*Nguồn: Task 17, báo cáo tuning seed 42.*

Trên tuning, rule weekend anomaly chỉ xét 40 claim (claim cuối tuần có phẫu thuật, đủ lịch sử), trong đó 20 claim gian lận và 20 claim hợp lệ. Recall 20/20 và precision 20/20 vì vậy dựa trên một mẫu rất nhỏ, và mọi claim gian lận đều nằm ở 8 bệnh viện được dựng sẵn để không có claim cuối tuần nào khác (L11).

## L14. Near-miss 0/55 chỉ chứng minh generator và engine nhất quán về ranh giới

*Nguồn: Task 17, báo cáo tuning seed 42.*

Không claim near-miss nào bị flag trên tuning. Điều này chỉ cho thấy generator đặt các claim sát biên đúng phía của ranh giới mà engine cài đặt (cùng quy ước R10). Nó không phải bằng chứng rằng rule tổng quát hoá tốt trên dữ liệu thật, nơi các ca sát biên không được thiết kế có chủ đích.

## L15. Rapid re-submission và amount clustering chỉ có tác dụng khi T ≤ 7

*Nguồn: Task 17, S3, L2.*

Rapid và clustering có trọng số 2, nên một claim chỉ kích hoạt một trong hai rule này có điểm round(100 × 2 / 28) = 7. Với T ≥ 8, recall của hai pattern này bằng 0 trừ khi claim kích hoạt thêm rule khác: trên tuning, recall tổng giảm từ 98,0% (T = 7) xuống 73,0% (T = 8). T = 7 vì vậy là điều kiện để hai rule này có tác dụng, và lựa chọn T gắn chặt với trọng số của chúng.

## L16. Hai thay đổi trong lúc đo held-out (minh bạch quy trình)

*Nguồn: Task 18a, 18b, 18c.*

- **Trước khi nhìn held-out:** test chấm 100.000 claim trong `FraudEngineTest` có giới hạn 120 giây, lệch Q19 (< 30 giây). Đã đổi thành 30 giây (commit `68e6309`); lần chạy đó mất 2.414 ms.
- **Sau khi đã thấy dòng tóm tắt held-out** (recall 96,5%, FPR chuẩn 4,4%, in ra khi chạy `evaluate`): phát hiện `metrics.json` chứa đường dẫn file đầu vào, nên hai lần chạy cùng dữ liệu ở hai đường dẫn khác nhau cho file khác nhau. Đã sửa **chỉ về trình bày** (commit `e2efa0d`): `metrics.json` chỉ còn SHA-256 của đầu vào, `metrics_report.md` chỉ ghi tên file và không còn đường dẫn tuyệt đối. Không đổi rule, trọng số, tham số, T hay generator.
- **Kiểm chứng bằng SHA-256** rằng không điểm hay metric nào thay đổi: `reports/tuning/scored_claims.json` giữ nguyên `b9e7e4fe…7096` (commit `cfdf676`); `reports/heldout/scored_claims.json` giữ nguyên `6bf48c7e…d9af` so với bản trước khi sửa; `metrics.json` của cả hai chỉ mất hai dòng `claims_file` và `config_file`, mọi giá trị khác giống hệt. Dataset held-out (`claims.csv`, `ground_truth.csv`) không đổi.
