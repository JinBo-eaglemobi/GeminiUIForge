import sys
import os
import json
import threading
import concurrent.futures

def process_single_image(img_path, out_path, session=None):
    try:
        from rembg import remove
        from PIL import Image
    except ImportError:
        print("Error: 'rembg' or 'Pillow' not installed. Please run: pip install rembg pillow")
        return False

    if not os.path.exists(img_path):
        print(f"Error: Input file not found: {img_path}")
        return False

    try:
        # 使用 PIL 打开图片以获取原始尺寸
        with Image.open(img_path) as img:
            original_size = img.size
            
            # 执行抠图（复用常驻 Session 避免模型重复加载）
            if session is not None:
                output_img = remove(img, session=session)
            else:
                output_img = remove(img)
            
            # 校验并修复尺寸
            if output_img.size != original_size:
                try:
                    resampling = Image.Resampling.LANCZOS
                except AttributeError:
                    resampling = Image.LANCZOS
                output_img = output_img.resize(original_size, resampling)
            
            # 确保输出目录存在
            out_dir = os.path.dirname(out_path)
            if out_dir and not os.path.exists(out_dir):
                os.makedirs(out_dir, exist_ok=True)
                
            # 显式保存为 PNG
            output_img.save(out_path, format="PNG")
        return True
    except Exception as e:
        print(f"Error processing image {img_path}: {str(e)}")
        import traceback
        traceback.print_exc()
        return False

def remove_background(input_path, output_path):
    success = process_single_image(input_path, output_path)
    if success:
        print(f"Success: Background removed and saved to {output_path}")
    return success

def batch_remove_background(manifest_path, max_workers=None):
    if not os.path.exists(manifest_path):
        print(f"Error: Manifest file not found: {manifest_path}")
        return False

    try:
        with open(manifest_path, "r", encoding="utf-8") as f:
            tasks = json.load(f)
    except Exception as e:
        print(f"Error reading manifest: {str(e)}")
        return False

    if not isinstance(tasks, list) or len(tasks) == 0:
        print("Warning: Manifest task list is empty.")
        return True

    # 自适应计算并发线程数（压榨多核算力，默认上限 8 线程避免内存爆仓）
    if max_workers is None or max_workers <= 0:
        cpu_num = os.cpu_count() or 4
        max_workers = min(cpu_num, 8)

    total = len(tasks)
    print(f"🚀 开始批量去背景任务，总待处理切片数: {total}，启用并发工作线程: {max_workers}")
    sys.stdout.flush()

    # 一次性预热加载 ONNX / rembg Session（模型全流程仅加载 1 次，跨多线程共享并发推理）
    session = None
    try:
        from rembg import new_session
        print("⚡ 正在初始化并常驻加载 AI 抠图模型 Session...")
        sys.stdout.flush()
        session = new_session()
        print("✅ AI 抠图模型 Session 加载完成，多线程并发高速推理就绪")
        sys.stdout.flush()
    except Exception as e:
        print(f"Warning: Failed to create persistent rembg session ({str(e)}), will fallback to default.")
        sys.stdout.flush()

    success_count = 0
    fail_count = 0
    progress_counter = 0
    progress_lock = threading.Lock()

    def _worker(item, current_idx):
        item_id = item.get("id", f"item_{current_idx}")
        item_name = item.get("name", item_id)
        input_path = item.get("input", "")
        output_path = item.get("output", input_path)
        ok = process_single_image(input_path, output_path, session=session)
        return item_id, item_name, ok

    # 多线程并发执行推理与落盘
    with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
        future_to_item = {
            executor.submit(_worker, item, idx): (idx, item)
            for idx, item in enumerate(tasks)
        }
        for future in concurrent.futures.as_completed(future_to_item):
            try:
                item_id, item_name, ok = future.result()
            except Exception as e:
                item_id = "unknown"
                item_name = "unknown"
                ok = False
                print(f"Worker exception: {e}", flush=True)

            with progress_lock:
                progress_counter += 1
                if ok:
                    success_count += 1
                else:
                    fail_count += 1
                # 结构化输出进度通知，供宿主进程流式捕获
                print(f"[BATCH_PROGRESS] {progress_counter}/{total}:{item_id}:{item_name}", flush=True)

    print(f"[BATCH_FINISHED] Completed {total} tasks: {success_count} succeeded, {fail_count} failed.", flush=True)
    return success_count > 0 or total == 0

if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "--batch":
        threads = None
        if len(sys.argv) >= 5 and sys.argv[3] == "--threads":
            try:
                threads = int(sys.argv[4])
            except ValueError:
                pass
        success = batch_remove_background(sys.argv[2], max_workers=threads)
        sys.exit(0 if success else 1)
    elif len(sys.argv) >= 3:
        success = remove_background(sys.argv[1], sys.argv[2])
        sys.exit(0 if success else 1)
    else:
        print("Usage: python remove_bg.py <input_path> <output_path>")
        print("   or: python remove_bg.py --batch <manifest_json_path> [--threads <count>]")
        sys.exit(1)
