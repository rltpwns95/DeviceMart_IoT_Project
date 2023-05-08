import cv2
import numpy as np
import matplotlib.pyplot as plt
import matplotlib.image as mpimg



def grayscale(img):
    return cv2.cvtColor(img, cv2.COLOR_RGB2GRAY)

def canny(img, low_threshold, high_threshold):
    return cv2.Canny(img, low_threshold, high_threshold)

def gaussian_blur(img, kernel_size):
    return cv2.GaussianBlur(img, (kernel_size, kernel_size), 0)

def region_of_interest(img, vertices):
    mask = np.zeros_like(img)
    if len(img.shape) > 2:
        channel_count = img.shape[2]
        ignore_mask_color = (255,) * channel_count
    else:
        ignore_mask_color = 255
        cv2.fillPoly(mask, vertices, ignore_mask_color)
        masked_image = cv2.bitwise_and(img, mask)
        return masked_image

def draw_lines(img, lines, color=[0, 255, 0], thickness=5):
    for line in lines:
        for x1, y1, x2, y2 in line:
            cv2.line(img, (x1, y1), (x2, y2), color, thickness)

def hough_lines(img, rho, theta, threshold, min_line_len, max_line_gap):
    lines = cv2.HoughLinesP(img, rho, theta, threshold, np.array([]),
                            minLineLength=min_line_len, maxLineGap=max_line_gap)
    line_img = np.zeros((img.shape[0], img.shape[1], 3), dtype=np.uint8)
    draw_lines(line_img, lines)
    return line_img

def weighted_img(img, initial_img, a=0.8, b=1., c=0.):
    return cv2.addWeighted(initial_img, a, img, b, c)


cap = cv2.VideoCapture("MDR_221009_024928.AVI")
fourcc = cv2.VideoWriter_fourcc(*'mp4v')
out = cv2.VideoWriter('output2.mp4', fourcc, 30.0, (int(cap.get(3)), int(cap.get(4))))

while(cap.isOpened()):
    ret, frame = cap.read()
    if ret:
        # Apply image processing pipeline
        gray = grayscale(frame)
        blur = gaussian_blur(gray, 5)
        edges = canny(blur, low_threshold=50, high_threshold=150)
        imshape = frame.shape
        vertices = np.array([[(600,imshape[0]-280),(900,imshape[0]-450),(1100,imshape[0]-450),(1400, imshape[0]-280)]], dtype=np.int32)
        
        vertices2 = np.array([[(600,imshape[0]-280),(900,imshape[0]-450),(950,imshape[0]-450),(800,imshape[0]-290)],
                     [(1100,imshape[0]-280),(1050,imshape[0]-450),(1080,imshape[0]-450),(1250, imshape[0]-280)]], dtype=np.int32)
        masked_edges = region_of_interest(edges, vertices2)
        line_image = hough_lines(masked_edges, 2, np.pi/180, 15, 40, 20)
        result = weighted_img(line_image, frame, 0.8, 1., 0.)
        
        # Write the processed frame to output video
        out.write(result)
        
        # Display the result
        cv2.imshow('result', result)
        # Press 'q' to quit
        if cv2.waitKey(1) & 0xFF == ord('q'):
            break


# Release resources
cap.release()
out.release()
cv2.destroyAllWindows()