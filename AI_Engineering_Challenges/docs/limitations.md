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
