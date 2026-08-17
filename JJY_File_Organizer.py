import os
import re
import shutil
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
from pathlib import Path
from datetime import datetime

DEST_BASE = Path(r"D:\개인자료\JJY DATA")
DESTS = {
    "MP3": DEST_BASE / "MP3",
    "MR": DEST_BASE / "MR",
    "IMAGE": DEST_BASE / "IMAGE",
    "PDF": DEST_BASE / "PDF",
    "악보": DEST_BASE / "악보",
    "한글문서": DEST_BASE / "한글문서",
    "MS문서": DEST_BASE / "MS문서",
    "압축파일": DEST_BASE / "압축파일",
    "캐드파일": DEST_BASE / "캐드파일",
    "미분류": DEST_BASE / "미분류",
}

IMAGE_EXTS = {".jpg",".jpeg",".jpe",".gif",".png",".tif",".tiff",".bmp",".webp",".heic",".svg",".ico",".raw",".cr2",".nef"}
HANGUL_EXTS = {".hwp",".hwpx",".hwt",".hml",".hwd"}
MS_OFFICE_EXTS = {
    ".doc",".docx",".docm",".dot",".dotx",".dotm",
    ".xls",".xlsx",".xlsm",".xlsb",".xlt",".xltx",".xltm",
    ".ppt",".pptx",".pptm",".pps",".ppsx",".ppsm",".pot",".potx",".potm",
    ".mdb",".accdb",".pub",".one",".onetoc2"
}
ARCHIVE_EXTS = {".zip",".7z",".rar",".tar",".gz",".bz2",".xz",".tgz",".tbz",".tbz2",".lz",".iso",".cab",".z"}
CAD_3D_EXTS = {
    ".dwg",".dxf",".dwf",".dwfx",".dwt",
    ".stp",".step",".igs",".iges",".stl",".obj",".fbx",".3ds",
    ".skp",".sldprt",".sldasm",".slddrw",".prt",".asm",".ipt",".iam",".idw",
    ".x_t",".x_b",".sat",".3dm",".ifc",".rvt",".rfa",".dae",".blend"
}
SHEET_RE = re.compile(r"(?:b[1-6]|[1-6]b|#[1-6]|[1-6]#|_C)$", re.IGNORECASE)

def classify(path: Path):
    stem = path.stem
    ext = path.suffix.lower()

    # 우선순위: MP3/MR -> 이미지 -> PDF/악보 -> 한글 -> MS -> 압축 -> CAD/3D -> 미분류
    if ext == ".mp3":
        if "mr" in stem.lower():
            return "MR", DESTS["MR"]
        return "MP3", DESTS["MP3"]

    if ext in IMAGE_EXTS:
        return "IMAGE", DESTS["IMAGE"]

    if ext == ".pdf":
        if SHEET_RE.search(stem):
            return "악보", DESTS["악보"]
        return "PDF", DESTS["PDF"]

    if ext in HANGUL_EXTS:
        return "한글문서", DESTS["한글문서"]

    if ext in MS_OFFICE_EXTS:
        return "MS문서", DESTS["MS문서"]

    if ext in ARCHIVE_EXTS:
        return "압축파일", DESTS["압축파일"]

    if ext in CAD_3D_EXTS:
        return "캐드파일", DESTS["캐드파일"]

    return "미분류", DESTS["미분류"]

def unique_destination(dest_dir: Path, filename: str):
    dest_dir.mkdir(parents=True, exist_ok=True)
    candidate = dest_dir / filename
    if not candidate.exists():
        return candidate
    p = Path(filename)
    n = 1
    while True:
        candidate = dest_dir / f"{p.stem} ({n}){p.suffix}"
        if not candidate.exists():
            return candidate
        n += 1

class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("JJY DATA 파일 정리 v2.0")
        self.geometry("1250x760")
        self.minsize(950, 600)
        self.items = []
        self._build_ui()

    def _build_ui(self):
        style = ttk.Style()
        try:
            style.theme_use("vista")
        except:
            pass

        top = ttk.Frame(self, padding=10)
        top.pack(fill="x")
        ttk.Label(top, text="정리할 폴더", font=("맑은 고딕", 10, "bold")).pack(side="left")
        self.folder_var = tk.StringVar()
        ttk.Entry(top, textvariable=self.folder_var, width=80).pack(side="left", padx=8, fill="x", expand=True)
        ttk.Button(top, text="폴더 선택", command=self.choose_folder).pack(side="left")
        ttk.Button(top, text="미리보기", command=self.preview).pack(side="left", padx=(8,0))

        rules = ttk.LabelFrame(self, text="자동 분류 규칙", padding=8)
        rules.pack(fill="x", padx=10)
        rule_text = (
            "MP3 → MP3 / 파일명에 MR 포함 → MR   |   이미지 → IMAGE   |   PDF → PDF / 지정 접미어 → 악보\n"
            "한글(HWP/HWPX 등) → 한글문서   |   Word/Excel/PowerPoint 등 → MS문서\n"
            "ZIP/7Z/RAR/TAR/GZ 등 → 압축파일   |   DWG/DXF/DWF/STP/STEP/IGS/STL/OBJ 등 → 캐드파일\n"
            "그 외 모든 파일 → 미분류"
        )
        ttk.Label(rules, text=rule_text, justify="left").pack(anchor="w")

        frame = ttk.Frame(self, padding=(10,10,10,0))
        frame.pack(fill="both", expand=True)
        cols = ("check","name","source","category","destination")
        self.tree = ttk.Treeview(frame, columns=cols, show="headings", selectmode="browse")
        headings = {"check":"정리","name":"파일명","source":"원본 위치","category":"분류","destination":"이동 위치"}
        widths = {"check":55,"name":250,"source":320,"category":90,"destination":430}
        for c in cols:
            self.tree.heading(c, text=headings[c])
            self.tree.column(c, width=widths[c], anchor="center" if c in ("check","category") else "w")
        ybar = ttk.Scrollbar(frame, orient="vertical", command=self.tree.yview)
        xbar = ttk.Scrollbar(frame, orient="horizontal", command=self.tree.xview)
        self.tree.configure(yscrollcommand=ybar.set, xscrollcommand=xbar.set)
        self.tree.grid(row=0, column=0, sticky="nsew")
        ybar.grid(row=0, column=1, sticky="ns")
        xbar.grid(row=1, column=0, sticky="ew")
        frame.rowconfigure(0, weight=1)
        frame.columnconfigure(0, weight=1)
        self.tree.bind("<Button-1>", self.toggle_check)

        bottom = ttk.Frame(self, padding=10)
        bottom.pack(fill="x")
        self.status_var = tk.StringVar(value="폴더를 선택하고 [미리보기]를 클릭하세요.")
        ttk.Label(bottom, textvariable=self.status_var).pack(side="left")
        ttk.Button(bottom, text="전체 선택", command=lambda:self.set_all(True)).pack(side="right")
        ttk.Button(bottom, text="전체 해제", command=lambda:self.set_all(False)).pack(side="right", padx=5)
        ttk.Button(bottom, text="선택 파일 정리 실행", command=self.execute).pack(side="right", padx=12)

    def choose_folder(self):
        folder = filedialog.askdirectory(title="정리할 폴더 선택 - 하위 폴더까지 검사합니다")
        if folder:
            self.folder_var.set(folder)

    def preview(self):
        root_text = self.folder_var.get().strip()
        root = Path(root_text)
        if not root.is_dir():
            messagebox.showwarning("폴더 확인", "정리할 폴더를 선택하세요.")
            return

        # 목적지 폴더 안을 다시 스캔하지 않도록 차단
        try:
            dest_inside_source = DEST_BASE.resolve().is_relative_to(root.resolve())
        except AttributeError:
            dest_inside_source = str(DEST_BASE.resolve()).lower().startswith(str(root.resolve()).lower())

        for iid in self.tree.get_children():
            self.tree.delete(iid)
        self.items = []
        counts = {}

        for path in root.rglob("*"):
            if not path.is_file():
                continue
            try:
                if dest_inside_source and DEST_BASE.resolve() in path.resolve().parents:
                    continue
            except:
                pass
            category, dest = classify(path)
            self.items.append({"source":path, "category":category, "dest_dir":dest, "checked":True})
            counts[category] = counts.get(category, 0) + 1

        for i, item in enumerate(self.items):
            self.tree.insert("", "end", iid=str(i), values=(
                "☑", item["source"].name, str(item["source"].parent),
                item["category"], str(item["dest_dir"])
            ))

        summary = ", ".join(f"{k} {v}" for k,v in counts.items())
        self.status_var.set(f"미리보기 완료: 총 {len(self.items)}개 | {summary}")

    def toggle_check(self, event):
        row = self.tree.identify_row(event.y)
        col = self.tree.identify_column(event.x)
        if row and col == "#1":
            idx = int(row)
            self.items[idx]["checked"] = not self.items[idx]["checked"]
            vals = list(self.tree.item(row, "values"))
            vals[0] = "☑" if self.items[idx]["checked"] else "☐"
            self.tree.item(row, values=vals)

    def set_all(self, checked):
        for i, item in enumerate(self.items):
            item["checked"] = checked
            vals = list(self.tree.item(str(i), "values"))
            vals[0] = "☑" if checked else "☐"
            self.tree.item(str(i), values=vals)

    def execute(self):
        selected = [i for i in self.items if i["checked"]]
        if not selected:
            messagebox.showinfo("알림", "정리할 파일을 하나 이상 선택하세요.")
            return

        answer = messagebox.askyesno(
            "파일 이동 확인",
            f"선택한 {len(selected)}개 파일을 실제로 이동합니다.\n\n"
            "중복 파일명은 (1), (2), (3)...을 붙입니다.\n"
            "원본 위치에서는 파일이 사라집니다.\n\n계속하시겠습니까?"
        )
        if not answer:
            return

        moved, errors = [], []
        for item in selected:
            src = item["source"]
            try:
                if not src.exists():
                    raise FileNotFoundError("원본 파일을 찾을 수 없습니다.")
                target = unique_destination(item["dest_dir"], src.name)
                shutil.move(str(src), str(target))
                moved.append((src, target, item["category"]))
            except Exception as e:
                errors.append((src, str(e)))

        DEST_BASE.mkdir(parents=True, exist_ok=True)
        log_path = DEST_BASE / f"정리내역_{datetime.now():%Y%m%d_%H%M%S}.txt"
        with log_path.open("w", encoding="utf-8-sig") as f:
            f.write("JJY DATA 파일 정리 내역\n")
            f.write("="*60 + "\n")
            f.write(f"실행 시간: {datetime.now():%Y-%m-%d %H:%M:%S}\n")
            f.write(f"성공: {len(moved)}개\n오류: {len(errors)}개\n\n")
            for src, target, category in moved:
                f.write(f"[{category}]\n{src}\n→ {target}\n\n")
            if errors:
                f.write("[오류 파일]\n")
                for src, err in errors:
                    f.write(f"{src}\n오류: {err}\n\n")

        self.status_var.set(f"정리 완료: 성공 {len(moved)}개 / 오류 {len(errors)}개")
        messagebox.showinfo(
            "정리 완료",
            f"정리가 완료되었습니다.\n\n성공: {len(moved)}개\n오류: {len(errors)}개\n\n"
            f"상세 내역:\n{log_path}"
        )
        self.preview()

if __name__ == "__main__":
    App().mainloop()
