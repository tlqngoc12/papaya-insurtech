# CLAUDE.md — AI Challenge 10: Fraud Detection Scoring Engine

## Mục tiêu

- Bài take-home tuyển dụng của Papaya Insurtech, **Challenge 10: Fraud Detection Scoring Engine**, viết bằng Java 21.
- Nộp **một link GitHub** chứa cả `Logical_Questions/` và `AI_Engineering_Challenges/`. Gốc git là `papaya-insurtech/`, nhánh `main`.
- Deadline nộp: **28/10**. Mục tiêu xong: **tối 27/10**.
- Kế hoạch 19 task và trạng thái hiện tại: `docs/PROGRESS.md`. Đọc file này đầu mỗi phiên.

## Nguồn sự thật

- `docs/AI_Challenge_10.md`: đề bài.
- `docs/design-decisions.md`: các quyết định thiết kế, kể cả phụ lục "Quyết định bổ sung Q1–Q24". Khi phụ lục khác với phần thân thì phụ lục được ưu tiên.
- Nếu hai nguồn mâu thuẫn nhau: **dừng lại hỏi**, không tự chọn.
- Không nạp cả `design-decisions.md` mỗi phiên. Chỉ đọc mục cần thiết (tìm theo mã như `R7`, `Q11`) khi task cần.

## Stack và quy ước

- Java 21, Maven, JUnit 5, Jackson (`jackson-databind`, `jackson-datatype-jsr310`, `jackson-dataformat-csv`).
- **Không thêm thư viện** nào khác nếu chưa hỏi.
- Package gốc: `com.papaya.fraud`. `pom.xml` ở `AI_Engineering_Challenges/`.
- Số tiền dùng `long`. Mean/SD tính bằng `double`.
- Ghi file bằng `"\n"` cố định, **không** dùng `System.lineSeparator()`. Repo có `.gitattributes` ép LF.

## Ràng buộc cứng

- Engine **không bao giờ** đọc `ground_truth.csv`. Chỉ module metric được đọc.
- T và trọng số chốt trên dataset tuning, **đóng băng** trước khi đo held-out.
- **Không chỉnh bất cứ thứ gì** sau khi đã nhìn kết quả held-out.
- Số liệu báo cáo phải là **số thật chạy ra**. Không ước lượng, không làm tròn đẹp, không viết số vào README khi chưa chạy.
- Ít nhất 15 unit test thật sự kiểm tra hành vi (mục tiêu 25–30).
- Chấm 2.000 claim dưới 30 giây.

## Quy trình mỗi task

1. Viết test cùng lúc với code.
2. Chạy `mvn -q test` (trong `AI_Engineering_Challenges/`).
3. Tóm tắt ngắn. Với task rule, kèm ví dụ số cụ thể: vài claim, rule nào kích hoạt, điểm ra bao nhiêu.
4. Cập nhật `docs/PROGRESS.md`.
5. **Dừng chờ xác nhận** rồi mới commit. Mỗi task một commit.

Ghi chú làm việc chỉ để ở `docs/PROGRESS.md`. Khi được bảo "ghi lại" thì ghi vào file đó. `README.md` chỉ dành cho người chấm bài.

## Khi gặp điều chưa có trong quyết định

- Quyết định thiết kế chưa có trong `design-decisions.md`: **dừng và hỏi**, không tự chọn.
- Gặp hạn chế: ghi ngay vào `docs/limitations.md`.

## Log AI

- Người dùng tự chạy `/export` và lưu vào `docs/ai-log/`.
- Trước khi `/clear` hoặc `/compact`: **nhắc người dùng export**.
- Không sửa nội dung các file log.

## Quy tắc trung thực

Nếu kết quả không đạt (recall < 70% hoặc FPR chuẩn > 20%): báo thật và phân tích nguyên nhân. **Không chỉnh generator** để cho ra số đẹp.
