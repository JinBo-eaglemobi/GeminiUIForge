import sys
import os

def remove_background(input_path, output_path):
    try:
        from rembg import remove
        from PIL import Image
    except ImportError:
        print("Error: 'rembg' or 'Pillow' not installed. Please run: pip install rembg pillow")
        return False

    try:
        if not os.path.exists(input_path):
            print(f"Error: Input file not found: {input_path}")
            return False

        # 使用 PIL 打开图片以获取原始尺寸
        with Image.open(input_path) as img:
            # 某些图片可能有 EXIF 旋转信息，Pillow 默认可能不会处理，
            # 但 rembg 处理后会丢失这些信息或导致尺寸对调。
            # 这里我们确保获取的是其呈现尺寸。
            original_size = img.size
            print(f"Original size: {original_size[0]}x{original_size[1]}")
            
            # 执行抠图
            output_img = remove(img)
            
            # 校验并修复尺寸
            if output_img.size != original_size:
                print(f"Size mismatch detected: {output_img.size[0]}x{output_img.size[1]} -> {original_size[0]}x{original_size[1]}. Correcting...")
                # 兼容性处理：优先使用新版 Pillow 的 Resampling 属性，否则回退
                try:
                    resampling = Image.Resampling.LANCZOS
                except AttributeError:
                    resampling = Image.LANCZOS
                
                output_img = output_img.resize(original_size, resampling)
            
            # 显式保存为 PNG
            output_img.save(output_path, format="PNG")
        
        print(f"Success: Background removed and saved to {output_path}")
        return True
    except Exception as e:
        print(f"Error processing image: {str(e)}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python remove_bg.py <input_path> <output_path>")
    else:
        success = remove_background(sys.argv[1], sys.argv[2])
        sys.exit(0 if success else 1)
