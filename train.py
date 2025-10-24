from ultralytics import YOLO

if __name__ == "__main__":
    model = YOLO('yolov8n.pt')

    model.train(
        data=r'E:\Kent Files Disc E\YOLO V8 FILES\Tests\MoreAppdev\data.yaml',
        imgsz=640,
        epochs=200,
        device=0,
        patience=20,
        cos_lr=True
    )