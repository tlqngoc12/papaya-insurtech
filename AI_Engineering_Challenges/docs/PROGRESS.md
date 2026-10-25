# PROGRESS

Cập nhật lần cuối: 2026-10-25.

## Trạng thái

| # | Task | Trạng thái | Commit |
|---|------|------------|--------|
| 01 | Skeleton Maven, CLI stub, limitations, ai-log | done | `eb2fc4b` |
| 02 | Danh mục mã (4 file JSON) và `CatalogLoader` | doing | |
| 03 | Config (`fraud-config.json`, `ConfigLoader`) | todo | |
| 04 | Đọc/ghi CSV và validate đầu vào (R11) | todo | |
| 05 | `DatasetIndex`, `Rule`, `Flag`, fixture builder | todo | |
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

Task 02. Danh mục đã duyệt (2026-10-25). Đang tạo 4 file JSON, `CatalogLoader` và `CatalogLoaderTest`.

## Việc tiếp theo

1. Sau khi bản nháp danh mục được duyệt: tạo 4 file JSON, `CatalogLoader`, `CatalogLoaderTest`, rồi dừng chờ review Task 02.
2. Task 03: `fraud-config.json` (trọng số mục G, tham số rule) và `ConfigLoader`.
3. Task 04: `ClaimCsvReader` / `ClaimCsvWriter` và validate R11.

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

## Ràng buộc cho các task sau

**Task 12 (generator hợp lệ):**
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
- File export ngày 25/10 đang ở `C:\Users\Zen\2026-10-25-142613-cd-dworkingrepositoriespapaya-insurtech.txt`, ngoài repo. Nhắc người dùng chuyển vào `docs/ai-log/`.

**Bản nháp danh mục Task 02** (chỉ nằm trong hội thoại, chưa có file):
- 21 chẩn đoán ICD-10 bản WHO, gồm 5 DENTAL, 7 OUTPATIENT, 7 INPATIENT, và 2 mã dùng cho cả hai loại (J18.9, M17.1).
- 40 procedure, trong đó 8 có cờ surgical (P-2006, P-2007, P-2009, P-2012, P-2013, P-2101, P-2102, P-3007). Bản đầu ghi nhầm là 9.
- Whitelist: 64 cặp riêng (đếm tay), cộng 35 cặp mã chung.
  - Mã chung: P-0001 (300), P-0002 (200).
  - Đánh số theo nhóm: P-1xxx ngoại trú, P-2xxx nội trú, P-3xxx nha khoa.
- 6 bundle: P-1101 diabetes, P-1102 cardiovascular, P-1103 UTI, P-2101 appendectomy, P-2102 cholecystectomy, P-3101 root canal.
- Procedure dùng cho clustering: P-2009 caesarean (42.000) và P-2010 coronary angiography (40.000).
  - Không chọn 38.000 vì cần μ ≥ 38.461 để số tiền 49.999 vẫn có z ≤ 2 (CV 15%).
- Quy tắc giá nội trú: μ ≤ 32.000 hoặc μ ≥ 95.000. Như vậy chỉ hai procedure clustering tạo FP clustering tự nhiên.
- Tổ hợp hợp lệ gợi ý:
  - J18.9: P-2001 + P-2002.
  - I21.9: P-2010 + P-2011.
  - I63.9: P-2003 + P-2001.
  - J06.9: P-1001 + P-1002.
