# PROGRESS

Cập nhật lần cuối: 2026-10-25.

## Trạng thái

| # | Task | Trạng thái | Commit |
|---|------|------------|--------|
| 01 | Skeleton Maven, CLI stub, limitations, ai-log | done | `eb2fc4b` |
| 02 | Danh mục mã (4 file JSON) và `CatalogLoader` | done | `d8b8e1e` |
| 03 | Config (`fraud-config.json`, `ConfigLoader`) | done | `205b36e` |
| 04 | Đọc/ghi CSV và validate đầu vào (R11) | done | `98cb920` |
| 05 | `DatasetIndex`, `Rule`, `Flag`, fixture builder | done | commit "Task 05" (mã điền ở lần cập nhật sau) |
| 06 | Rule duplicate và rapid re-submission | todo | |
| 07 | Rule upcoding | todo | |
| 08 | Rule unbundling và diagnosis-procedure mismatch | todo | |
| 09 | Rule phantom billing và amount clustering | todo | |
| 10 | Rule weekend anomaly | todo | |
| 11 | Chấm điểm, xếp hạng, `FraudEngine`, subcommand `score`, đo thời gian | todo | |
| 12 | Generator: phần claim hợp lệ, subcommand `generate`, ground truth | todo | |
| 13 | Generator gian lận (1): duplicate, rapid, upcoding, unbundling | todo | |
| 14 | Generator gian lận (2): phantom, weekend, mismatch, clustering | todo | |
| 15 | Near-miss và noise | todo | |
| 16 | Metric, subcommand `evaluate` và `tune` | todo | |
| 17 | Tuning (seed 42) và commit đóng băng `config/frozen-config.json` | todo | |
| 18 | Held-out (seed 2024) và seed ổn định 101–105 | todo | |
| 19 | Writeup: README, interpretations, timeline, limitations, ai-log | todo | |

Commit ngoài kế hoạch: `d335972` Add .gitattributes to normalize line endings to LF.

## Đang làm

Task 05 đã commit: 54 test xanh. Bắt đầu Task 06.

## Việc tiếp theo

1. Điền mã commit Task 05 vào bảng trạng thái.
2. Task 06: rule duplicate và rapid re-submission.
3. Task 07: rule upcoding.

## Câu hỏi còn mở / chờ quyết

- [ ] **Q1**: chờ người dùng gửi nội dung để điền vào phụ lục `design-decisions.md`.
- [ ] **Q5**: chờ người dùng gửi nội dung để điền vào phụ lục.
- [ ] **README mục lục ở gốc `papaya-insurtech/`**: người dùng nói "thêm sau", chưa gắn vào task nào.

## Quyết định đã chốt trong phiên làm việc

- 2026-10-25: Người dùng điền phần "Quyết định" và "Trọng số" trong `design-decisions.md`.
- 2026-10-25: Trả lời Q1–Q24. Q2–Q4 và Q6–Q24 đã ghi vào phụ lục; Q1, Q5 chờ nội dung.
- 2026-10-25: Duyệt kế hoạch 19 task, không cắt task; dùng sáng 28/10 làm buffer.
- 2026-10-25: Gốc git chuyển về `papaya-insurtech/`. Đã xoá repo con rỗng trong `AI_Engineering_Challenges/`.
- 2026-10-25: Làm thẳng trên `main`, mỗi task một commit, không amend commit Task 01.
- 2026-10-25: Danh tính git đặt cho repo: `Ngoc Tran Le Que <tlqngoc12@gmail.com>`.
- 2026-10-25: `Logical_Questions/README.md` commit chung với Task 01.
- 2026-10-25: `docs/interview-defense.md` không đưa vào repo (người dùng tự chuyển ra ngoài).
- 2026-10-25: Thêm `.gitattributes` (`* text=auto eol=lf`, binary cho jar/png/jpg), commit riêng.
- 2026-10-25: Log AI giữ nguyên văn file `.txt` từ `/export` trong `docs/ai-log/`, không chuyển đổi.
- 2026-10-25: Tạo `CLAUDE.md` và `docs/PROGRESS.md`. Ghi chú làm việc chỉ để ở `PROGRESS.md`; `README.md` chỉ dành cho người chấm.
- 2026-10-25: Task 02, ràng buộc generator:
  - (c)1: với **mọi** procedure có giá gốc ≥ 15.000, nếu số tiền upcoding rơi vào dải clustering thì lấy lại hệ số. Upcoding chỉ nhắm procedure (nhóm chữ ký) có ít nhất 10 claim.
  - (c)2: nhiễu claim unbundled cắt ≤ 47.000. Đã ghi vào `limitations.md` (L5).
  - (c)3: claim hợp lệ luôn dùng mã gói.
  - (c)4: mã chung chỉ xuất hiện như claim một procedure.
- 2026-10-25: Task 02, giữ cặp J06.9 + P-1002. P-1001 thêm INPATIENT (dùng cho K35.8).
- 2026-10-25: Task 02, `CatalogLoaderTest` phải tự đếm số cặp whitelist và kiểm tra mỗi cặp hợp lệ về claim_type.
- 2026-10-25: Task 02, duyệt danh mục.
  - P-0002 giữ claim_type O, D.
  - Mã chung chỉ hợp lệ với chẩn đoán có cùng claim_type: P-0001 với 21 chẩn đoán, P-0002 với 14 chẩn đoán (có O hoặc D), tổng 35 cặp mã chung.
  - PCI (P-2011) và coronary angiography (P-2010) giữ `surgical = false`. Ghi vào `limitations.md` (L6).
- 2026-10-25: Task 02, sức chứa dải clustering: chọn **phương án 3**. 4 nhóm chứa claim trong dải: P-2009, P-2010, I21.9 (P-2010 + P-2001), O82 (P-2009 + P-2001).
- 2026-10-25: Thứ tự commit: `.gitattributes` (đã có ở `d335972`), rồi `CLAUDE.md` + `PROGRESS.md` + `limitations.md`, rồi Task 02 chỉ gồm danh mục.
- 2026-10-25: Task 02, thử phá có chủ ý 3 lần (giá P-2008, bỏ thành phần của P-2102 khỏi K80.2, thêm E11.9 + P-3003): cả 3 lần đều có test đỏ.
  - Không sửa `CatalogLoaderTest` để đọc JSON thô, vì `Catalog.create` đã chặn lỗi với thông báo chính xác và `CatalogTest` đã kiểm tra từng loại lỗi.
  - Không thêm snapshot test. Ghi lỗ hổng "tráo cặp" vào `limitations.md` (L7).
  - Commit Task 02 gồm danh mục, test, `limitations.md` và `PROGRESS.md`.
- 2026-10-25: Task 03, config.
  - `fraud-config.json` không chứa T. T chỉ có trong `config/frozen-config.json`, do `tune` ghi ra (cùng schema, thêm `threshold`).
  - `score` không cần T. Lỗi thiếu T (`requireThreshold()`) chỉ xảy ra ở `evaluate` và `all`. Không có T mặc định.
  - Thêm `rules.amount_clustering.auto_approval_threshold = 50000`, kiểm tra `upper_bound < auto_approval_threshold`. Evidence clustering lấy con số 50.000 từ trường này. Cả ba biên clustering đọc thành `long`, có test với giá trị vượt `Integer.MAX_VALUE`.
  - Ràng buộc tham số: `min_group_size ≥ 2`, `window_days ≥ 1`, `max_weekend_ratio` trong (0; 1), `threshold` 1–100.
- 2026-10-25: Task 04, đọc/ghi CSV và validate.
  - `claim_id` trùng: giữ dòng đầu, loại các dòng sau.
  - Header sai: không đọc file, trả về 1 dòng bị loại ở dòng 1. Thông báo nêu rõ cột lạ và cột thiếu. Header có thêm `is_fraud` bị từ chối (bảo vệ ràng buộc "engine không đọc ground truth").
  - `procedure_codes` có mã rỗng (ví dụ `P-1001|`): loại dòng.
  - Bắt buộc khác rỗng: `claim_id`, `member_id`, `provider_id`, `claim_date`, `claim_type`, `diagnosis_code`, `submitted_amount`. `provider_name` được rỗng; `is_weekend` rỗng chỉ cảnh báo.
  - `is_weekend` không phân biệt hoa thường (Excel lưu `TRUE`/`FALSE`); `claim_type` phải viết hoa đúng.
  - CSV hỏng cấu trúc: dừng đọc, giữ các dòng đã đọc trước đó. Số dòng là dòng bắt đầu của row bị hỏng; thông báo kèm dòng nơi parser dừng (Jackson chỉ phát hiện thiếu nháy đóng ở cuối file).
  - Không giới hạn `claim_date` trong năm 2024.
- 2026-10-25: Task 05, `DatasetIndex`.
  - Đếm cuối tuần dùng `Claim.isWeekend()` (reader đã tính lại theo R11), không tính lại lần hai.
  - Chữ ký procedure = các mã khác nhau, sắp xếp, nối bằng `|`; mã lặp trong một claim tính là một.
  - `DatasetIndex.of` ném `IllegalArgumentException` khi `claim_id` trùng (lỗi lập trình, reader đã loại trước).
  - `weekendRatioExcluding` trả rỗng khi provider không còn claim nào khác; ném lỗi khi claim không thuộc index.
  - `Rule` là interface thường, seal ở Task 10.

## Ràng buộc cho các task sau

**Task 07 (rule upcoding):**
- SD = NaN (nhóm 1 claim) hoặc SD = 0 thì không flag (Q11).

**Task 10 (rule weekend, rule cuối cùng):**
- Đổi `Rule` thành `sealed interface ... permits` 8 class rule. Ở Task 05 chưa seal được vì javac báo `sealed class must have subclasses` khi chưa có class con (đã thử).

**Task 11 (lệnh `score`):**
- In ra số dòng bị loại và số cảnh báo khi đọc `claims.csv`.

**Task 12 (generator hợp lệ):**
- Dataset do generator sinh ra, khi đọc lại bằng `ClaimCsvReader`, phải cho **0 dòng bị loại và 0 cảnh báo**. Sẽ có test kiểm tra.
- Claim chỉ có P-0001 với chẩn đoán chỉ nội trú phải có trọng số rất thấp.
- Số lượng claim của 4 nhóm phương án 3 do generator quyết, nhưng: mỗi nhóm **ít nhất 30 claim**, và claim cài (clustering gian lận + R9 `legit_noise`) chiếm **không quá 25%** mỗi nhóm. Sẽ có test kiểm tra.

**Task 13 (upcoding, unbundling):**
- Upcoding: với mọi procedure có giá gốc ≥ 15.000, nếu số tiền rơi vào dải clustering thì lấy lại hệ số. Chỉ nhắm nhóm chữ ký có ít nhất 10 claim.
- Unbundling: nhiễu cắt ≤ 47.000.

**Task 14 (clustering, weekend):**
- Claim clustering dùng P-2009 (phẫu thuật) phải đặt vào ngày thường, hoặc ở provider không thuộc nhóm "chỉ làm ngày thường", để không kích hoạt weekend anomaly.

## Ghi chú cho phiên sau

**Môi trường:**
- Shell reset thư mục về `C:\Users\Zen` sau mỗi lệnh, nên luôn dùng đường dẫn tuyệt đối.
- Git hệ thống đặt `core.autocrlf=true`; `.gitattributes` của repo ép LF.
- PowerShell 5.1 `Out-File -Encoding utf8` ghi BOM. Commit message nên truyền bằng `git commit -m ... -m ...`, không qua file tạo bằng PowerShell.
- Nhánh `main` đang trỏ upstream `origin/main`, nhưng remote chưa có nhánh này ("upstream is gone"). Hết khi push lần đầu bằng `git push -u origin main`. Chưa push lần nào.
- Hai file export ngày 25/10 đang ở ngoài repo, cần nhắc người dùng chuyển vào `docs/ai-log/`:
  - `C:\Users\Zen\2026-10-25-142613-cd-dworkingrepositoriespapaya-insurtech.txt`
  - `C:\Users\Zen\2026-10-25-154311-cd-dworkingrepositoriespapaya-insurtech.txt`

**Danh mục Task 02** (đã có file trong `src/main/resources/catalog/`, đây là tóm tắt lý do thiết kế):
- 21 chẩn đoán ICD-10 bản WHO, gồm 5 DENTAL, 7 OUTPATIENT, 7 INPATIENT, và 2 mã dùng cho cả hai loại (J18.9, M17.1).
- 40 procedure, trong đó 8 có cờ surgical (P-2006, P-2007, P-2009, P-2012, P-2013, P-2101, P-2102, P-3007).
  - Mã chung: P-0001 (300), P-0002 (200).
  - Đánh số theo nhóm: P-1xxx ngoại trú, P-2xxx nội trú, P-3xxx nha khoa.
- Whitelist: 64 cặp riêng, cộng 35 cặp mã chung.
- 6 bundle: P-1101 diabetes, P-1102 cardiovascular, P-1103 UTI, P-2101 appendectomy, P-2102 cholecystectomy, P-3101 root canal.
- Procedure dùng cho clustering: P-2009 caesarean (42.000) và P-2010 coronary angiography (40.000).
  - Không chọn 38.000 vì cần μ ≥ 38.461 để số tiền 49.999 vẫn có z ≤ 2 (CV 15%).
- Quy tắc giá nội trú: μ ≤ 32.000 hoặc μ ≥ 95.000. Như vậy chỉ hai procedure clustering tạo FP clustering tự nhiên.
- Tổ hợp hợp lệ gợi ý:
  - J18.9: P-2001 + P-2002.
  - I21.9: P-2010 + P-2011.
  - I63.9: P-2003 + P-2001.
  - J06.9: P-1001 + P-1002.
