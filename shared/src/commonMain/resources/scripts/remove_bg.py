# -*- coding: utf-8 -*-
"""
优化版通用图像背景去除脚本 (remove_bg.py)
专为游戏 UI 图元、复杂雪碧图/切片集、单体道具及精细带底座组件打造。

核心特性与技术突破：
1. 多图元雪碧图自适应解耦 (Multi-Object Asset Sheet Decoupling)：
   解决全局 SOD/U2Net 在面对多图元大图时显著性崩塌、四周小图标被当背景吃掉的致命痛点。
   纯色底图自动执行连通域拆解，每个图元获得 100% 独立显著性推理，再无缝回贴全景。
2. 色彩无损保真 (Color Fidelity Invariant)：
   彻底废弃 rembg 污染/涂黑的 RGB，100% 回溯原图真实光影色彩，杜绝黑色剪影假死。
3. 颜色反污染消白边 (Color Decontamination / Defringing)：
   基于物理光学 Unmultiply 模型，对边缘半透明抗锯齿过渡区反向解算剥离背景底色，
   深色背景下零发光白边、零脏晕。
4. 漫反射脏投影剥离 (Drop Shadow Stripping)：
   自适应剥离白底模型渲染时产生的浅灰漫反射脏阴影，还原本体干净利落的外轮廓。
5. 底座保护与高光平衡 (Baseplate Protection & Highlight Balance)：
   对内部低对比度纹理、宝石高光点进行自适应保真，防止被误判为镂空孔洞。
"""

import sys
import os
import json
import threading
import concurrent.futures
import numpy as np
from PIL import Image

try:
    from scipy.ndimage import (
        label,
        binary_fill_holes,
        binary_dilation,
        binary_erosion,
        generate_binary_structure
    )
except ImportError:
    pass


def decontaminate_color(rgb_arr, alpha_arr, bg_color):
    """
    边缘半透明颜色反污染（Defringing / Unmultiply）：
    当物体在纯色（如纯白）背景下渲染时，边缘半透明像素混合了背景色：
    C_observed = alpha * C_foreground + (1 - alpha) * C_background
    因此反向恢复前景真实色：
    C_foreground = (C_observed - (1 - alpha) * C_background) / alpha
    """
    alpha_f = alpha_arr.astype(np.float32) / 255.0
    # 仅针对半透明过渡区处理 (0.02 < alpha < 0.98)
    mask = (alpha_f > 0.02) & (alpha_f < 0.98)
    if not np.any(mask):
        return rgb_arr.copy()

    clean_rgb = rgb_arr.astype(np.float32).copy()
    a = alpha_f[mask, np.newaxis]
    bg = bg_color.astype(np.float32)
    obs = clean_rgb[mask]

    # 反向解算前景原色
    fg = (obs - (1.0 - a) * bg) / np.maximum(a, 0.05)
    clean_rgb[mask] = np.clip(fg, 0, 255)
    return clean_rgb.astype(np.uint8)


def process_single_isolated_block(crop_rgb, session, bg_color):
    """
    对单个独立图元切片进行超精细抠图与边缘精修
    """
    from rembg import remove
    w, h = crop_rgb.size
    orig_np = np.array(crop_rgb)

    # 1. rembg 局部显著性推理
    rembg_res = remove(crop_rgb, session=session)
    rembg_np = np.array(rembg_res)
    rembg_alpha = rembg_np[:, :, 3].copy()

    # 2. 外部连通背景判定
    diff = np.linalg.norm(orig_np.astype(np.float32) - bg_color.astype(np.float32), axis=-1)
    
    # 漫反射浅色投影剔除阈值：白底投影通常 diff < 30~35
    bg_candidate = diff < 32.0

    border_mask = np.zeros((h, w), dtype=bool)
    border_mask[0, :] = True
    border_mask[-1, :] = True
    border_mask[:, 0] = True
    border_mask[:, -1] = True

    struct = np.array([[0, 1, 0], [1, 1, 1], [0, 1, 0]], dtype=bool)
    labeled, num_features = label(bg_candidate, structure=struct)
    border_lbls = np.unique(labeled[border_mask])
    border_lbls = border_lbls[border_lbls != 0]
    true_ext_bg = np.isin(labeled, border_lbls)

    # 3. 底座实体保护：
    # 不是外部连通背景，且与背景存在明显色差的实体像素，强化其不透明度
    strong_fg = (~true_ext_bg) & (diff > 35.0)

    # 组合 Alpha
    alpha = np.maximum(rembg_alpha, np.where(strong_fg, 255, 0).astype(np.uint8))

    # 外部连通背景强制为 0
    alpha[true_ext_bg & (diff < 22.0)] = 0

    # 边缘白底浅灰投影软衰减：对于外部连通边缘浅灰区域 (diff 在 15~32 之间)，压制 alpha
    shadow_mask = true_ext_bg & (diff >= 15.0) & (diff < 32.0)
    alpha[shadow_mask] = np.clip(alpha[shadow_mask].astype(np.float32) * 0.15, 0, 255).astype(np.uint8)

    # 4. 内部高光点保护（被实体包围的微小同色高光点不挖空）
    cavity = (alpha < 128) & (~true_ext_bg)
    cav_lbl, cav_num = label(cavity)
    for i in range(1, cav_num + 1):
        c_mask = cav_lbl == i
        c_area = np.sum(c_mask)
        # 微小高光点（面积 < 120 且四周均为实体），恢复实心
        if c_area < 120:
            alpha[c_mask] = 255

    # 5. 反污染消白边 (Unmultiply)
    clean_rgb = decontaminate_color(orig_np, alpha, bg_color)

    # 组合为 RGBA
    res_rgba = np.dstack([clean_rgb, alpha])
    return Image.fromarray(res_rgba)


def detect_components_and_islands(rgb_arr, bg_color):
    """
    检测画面中的所有独立图元连通岛
    """
    h, w, _ = rgb_arr.shape
    diff = np.linalg.norm(rgb_arr.astype(np.float32) - bg_color.astype(np.float32), axis=-1)

    # 实体前景二值初筛
    fg_binary = diff > 28.0

    # 适度膨胀 3x3 连通紧邻的细微笔画，但不串联不同图元
    struct = np.ones((3, 3), dtype=bool)
    dilated = binary_dilation(fg_binary, structure=struct, iterations=1)

    lbl, num = label(dilated)
    boxes = []
    for i in range(1, num + 1):
        ys, xs = np.where(lbl == i)
        if len(ys) < 120:  # 过滤微小杂质噪点
            continue
        min_x, max_x = int(xs.min()), int(xs.max())
        min_y, max_y = int(ys.min()), int(ys.max())
        boxes.append((min_x, min_y, max_x + 1, max_y + 1))

    return boxes


def merge_overlapping_boxes(boxes, expand_pad=6):
    """
    合并重叠或距离极近的切片候选框
    """
    if not boxes:
        return []

    rects = []
    for b in boxes:
        rects.append([b[0] - expand_pad, b[1] - expand_pad, b[2] + expand_pad, b[3] + expand_pad])

    changed = True
    while changed:
        changed = False
        new_rects = []
        skip = set()
        for i in range(len(rects)):
            if i in skip:
                continue
            r1 = rects[i]
            for j in range(i + 1, len(rects)):
                if j in skip:
                    continue
                r2 = rects[j]
                # 检查相交
                if not (r1[2] < r2[0] or r1[0] > r2[2] or r1[3] < r2[1] or r1[1] > r2[3]):
                    # 合并
                    r1 = [min(r1[0], r2[0]), min(r1[1], r2[1]), max(r1[2], r2[2]), max(r1[3], r2[3])]
                    skip.add(j)
                    changed = True
            new_rects.append(r1)
        rects = new_rects

    # 还原并去除 padding 膨胀
    merged = []
    for r in rects:
        merged.append((r[0] + expand_pad, r[1] + expand_pad, r[2] - expand_pad, r[3] - expand_pad))
    return merged


def remove_background_pipeline(img, session=None):
    """
    全自动背景去除主流程：
    自动判断单图元或多图元雪碧图，执行最佳策略。
    """
    from rembg import remove, new_session
    if session is None:
        try:
            print("正在加载 Rembg 深度学习模型权重 (u2net)...", flush=True)
            session = new_session("u2net")
            print("深度学习模型加载完成，开始分析画面布局与背景...", flush=True)
        except Exception:
            session = None

    orig_rgb = img.convert("RGB")
    rgb_arr = np.array(orig_rgb)
    h, w, _ = rgb_arr.shape

    # 1. 边缘四角背景采样
    pad = min(4, w, h)
    corner_samples = [
        rgb_arr[:pad, :pad].reshape(-1, 3),
        rgb_arr[:pad, -pad:].reshape(-1, 3),
        rgb_arr[-pad:, :pad].reshape(-1, 3),
        rgb_arr[-pad:, -pad:].reshape(-1, 3)
    ]
    all_corners = np.vstack(corner_samples)
    corner_std = float(np.std(all_corners, axis=0).mean())
    bg_color = np.median(all_corners, axis=0)

    # 2. 判断是否属于均匀纯色背景
    is_uniform_bg = corner_std < 22.0

    if is_uniform_bg:
        # 尝试检测多图元独立组件
        boxes = detect_components_and_islands(rgb_arr, bg_color)

        # 如果检测到 2 个以上相互独立的图元切片，启动多组件解耦切片加速通道
        if len(boxes) >= 2:
            print(f"识别到雪碧图模式：共检测到 {len(boxes)} 个独立图元实体，开始局部高精度推理...", flush=True)
            full_canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
            
            pad_margin = 12
            for idx, box in enumerate(boxes):
                print(f"正在高精度去背推理: [{idx + 1}/{len(boxes)}]...", flush=True)
                bx1 = max(0, box[0] - pad_margin)
                by1 = max(0, box[1] - pad_margin)
                bx2 = min(w, box[2] + pad_margin)
                by2 = min(h, box[3] + pad_margin)

                crop_img = orig_rgb.crop((bx1, by1, bx2, by2))
                matted_crop = process_single_isolated_block(crop_img, session, bg_color)

                # 将带透明度的切片合成贴入画布，使用 paste 的第三个参数作为 mask
                full_canvas.paste(matted_crop, (bx1, by1), matted_crop)

            return full_canvas

    # 3. 单图元或复杂背景通道：走整体精修流程
    print("单主体/复杂背景模式：执行全局精细抠图与边缘反污染消白边...", flush=True)
    if session is not None:
        rembg_out = remove(orig_rgb, session=session)
    else:
        rembg_out = remove(orig_rgb)

    rembg_np = np.array(rembg_out)
    alpha = rembg_np[:, :, 3].copy()

    if is_uniform_bg:
        diff = np.linalg.norm(rgb_arr.astype(np.float32) - bg_color.astype(np.float32), axis=-1)

        border_mask = np.zeros((h, w), dtype=bool)
        border_mask[0, :] = True
        border_mask[-1, :] = True
        border_mask[:, 0] = True
        border_mask[:, -1] = True

        struct = np.array([[0, 1, 0], [1, 1, 1], [0, 1, 0]], dtype=bool)
        bg_candidate = diff < 30.0
        labeled, num_features = label(bg_candidate, structure=struct)
        border_lbls = np.unique(labeled[border_mask])
        border_lbls = border_lbls[border_lbls != 0]
        true_ext_bg = np.isin(labeled, border_lbls)

        # 保护实体底座
        strong_fg = (~true_ext_bg) & (diff > 35.0)
        alpha = np.maximum(alpha, np.where(strong_fg, 255, 0).astype(np.uint8))
        alpha[true_ext_bg & (diff < 20.0)] = 0

        # 反污染消白边
        clean_rgb = decontaminate_color(rgb_arr, alpha, bg_color)
    else:
        clean_rgb = rgb_arr

    res_arr = np.dstack([clean_rgb, alpha])
    return Image.fromarray(res_arr)


def process_single_image(img_path, out_path, session=None):
    if not os.path.exists(img_path):
        print(f"Error: Input file not found: {img_path}", flush=True)
        return False

    try:
        print(f"正在读取并解码待处理原图: {os.path.basename(img_path)}", flush=True)
        with Image.open(img_path) as img:
            original_size = img.size
            print(f"原图分辨率: {original_size[0]} × {original_size[1]}，准备启动去背流水线...", flush=True)
            result_img = remove_background_pipeline(img, session=session)

            if result_img.size != original_size:
                try:
                    resampling = Image.Resampling.LANCZOS
                except AttributeError:
                    resampling = Image.LANCZOS
                result_img = result_img.resize(original_size, resampling)

            out_dir = os.path.dirname(out_path)
            if out_dir and not os.path.exists(out_dir):
                os.makedirs(out_dir, exist_ok=True)

            print("正在编码保存透明 PNG 图像至磁盘...", flush=True)
            result_img.save(out_path, format="PNG")
            print("原图去背完成，透明底图已就绪", flush=True)
        return True
    except Exception as e:
        print(f"Error processing image {img_path}: {str(e)}", flush=True)
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

    if max_workers is None or max_workers <= 0:
        cpu_num = os.cpu_count() or 4
        max_workers = min(cpu_num, 8)

    total = len(tasks)
    print(f"🚀 开始批量去背景任务，总待处理切片数: {total}，启用并发工作线程: {max_workers}")
    sys.stdout.flush()

    session = None
    try:
        from rembg import new_session
        print("⚡ 正在初始化并常驻加载 AI 抠图模型 Session...")
        sys.stdout.flush()
        session = new_session("u2net")
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
