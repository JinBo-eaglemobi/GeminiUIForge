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

        with open(input_path, 'rb') as i:
            input_data = i.read()
            output_data = remove(input_data)
            
            with open(output_path, 'wb') as o:
                o.write(output_data)
        
        print(f"Success: Background removed and saved to {output_path}")
        return True
    except Exception as e:
        print(f"Error processing image: {str(e)}")
        return False

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python remove_bg.py <input_path> <output_path>")
    else:
        success = remove_background(sys.argv[1], sys.argv[2])
        sys.exit(0 if success else 1)
