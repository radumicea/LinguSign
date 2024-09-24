# LinguSign: A Bidirectional Romanian Sign Language Translator

## Video Presentation

https://github.com/user-attachments/assets/20f436e5-8d1b-4059-8e2f-792d6c7613e3

#### Video Description:
This is the demo video that showcases the **LinguSign** application.

In the first part, the **Romanian (RO) to Romanian Sign Language (RSL)** module is shown. Once the button is pressed, the application starts listening, recognizes the speech, and starts matching it with words in the known vocabulary. Afterwards, for each matched word, the rotations necessary to align the model's joints with the motion of the gestured sign are computed, based on which the model is animated. The spoken sentence is as follows: *"Acum că am apasat butonul, după cum puteți vedea, modelul va începe să facă semnele corespunzătoare cuvintelor spuse."* which translates to *"Now that I have pressed the button, as you can see, the model will start doing the signs corresponding to the spoken words."*.

In the second part, the **RSL to RO** module is shown. It uses the device's camera to record footage, and matches the body's movements with words it was trained on. These words are part of a constrained vocabulary, one where a single word is representative for entire word families and semantic fields. Here I gesture the signs for a stanza from [Luceafărul (The Morning Star)](https://ro.wikisource.org/wiki/Luceaf%C4%83rul_(Eminescu)), a Romanian poem. These signs are successfully translated and then spoken out loud.

---

## Project Overview

**LinguSign** is a mobile application designed for **real-time, bidirectional translation** between **RSL** and **spoken Romanian**. This project was inspired by a personal connection to the deaf community and the current lack of automated translation tools for sign language.

### Key Features:
1. **RO to RSL Translation**: Translates spoken Romanian into RSL signs gestured by a 3D animated model.
2. **RSL to RO Translation**: Recognizes gestured signs and translates them into spoken Romanian using the device's camera and a custom ML model.
3. **Portable and Real-time**: Runs efficiently on mid-range Android devices, enabling portable translation.

---

## Technical Overview

### 1. System Architecture
- **Backend**:
  - Web API, database, and NLP service for matching the vast Romanian vocabulary to the constrained RSL known vocabulary.
  - **ChatGPT API** for building coherent sentences from classified words.  
![backend](https://github.com/user-attachments/assets/56457ed6-39ea-4af3-a14f-22875ad12b9b)

- **Client Application**:
  - **RO to RSL**: *WordsRecognizer* sends spoken words to the backend, which matches them with the known vocabulary. Based on this response, the animations are created.  
  ![animation_fragment](https://github.com/user-attachments/assets/6807e60e-1d66-417e-8df1-d04a6112b427)

  - **RSL to RO**: *Landmarker* extracts hand and body landmarks, while *Translator* classifies gestures into Romanian words.  
  ![camera_fragment](https://github.com/user-attachments/assets/63818cc1-2484-4060-9f92-957e3c24466d)


### 2. Technologies and Algorithms Used
- **MediaPipe by Google**:
  - **MediaPipe Hands**: Extracts hand landmarks.
  - **MediaPipe Pose**: Extracts body landmarks.
  
- **Kalman Filter**: Corrects noisy gesture data for accuracy.
![Kalman](https://github.com/user-attachments/assets/c5b8e9d0-26c1-4356-87db-c92faf6b3678)  
*The X and Y coordinates of the index finger tip for some gestured sign  
plotted against time before and after applying the Kalman Filter. Notice  
the correction of noisy/missing data.*

- **Moving Average Filter**: Smooths gestures for a more natural animation.
![sma](https://github.com/user-attachments/assets/2158570e-b508-477d-bf7a-26c753fd1ba1)
*The X and Y coordinates of the index finger tip for some gestured sign for different Moving Average window sizes. Notice how too large a window size negatively affects the accuracy of the landmarks, by smoothing the series of points too much*

- **NLP**: Natural sentences matched to the constrained RSL vocabulary using NLP techniques like **lemmatization** and **stemming**, and metrics like **Levenshtein** and **cosine distances**.

- **Inverse Kinematics**: Used to compute rotations and then animate a 3D model based on landmarks in the known vocabulary.

- **Feature Engineering**: Derived a method of transforming the raw landmarks returned by MediaPipe into usable features for the purpose of gesture classification.

- **TensorFlow**: Created and trained an ML model composed of LSTM and dense layers to translate from gestures to words, achieving high accuracy on the test dataset.

---

## Results & Achievements

- **Performance**: The application runs smoothly on mid-range Android devices.
- **Gesture Recognition Accuracy**:  
  - **100% accuracy** on test data for gesture recognition.
  - **99.89% confidence** in gesture predictions.
- **Interpreter Feedback**: Animated gestures were verified by a human interpreter for accuracy.

---

## Future Work

1. Update to the new version **MediaPipe Holistic** once available for better landmark extraction.
2. Expand the dataset by recording an expert in RSL.
3. Implement **sign language segmentation algorithms** to improve translation reliability.
4. Use semantic ranking instead of the NLP approach.

---

Thank you for reading! If you want a more in-depth overview of the project, please consider looking over the project's presentation: [Micea_Radu-Cătălin_Presentation_CTIEN_License.pdf](https://github.com/radumicea/LinguSign/blob/main/Micea_Radu-C%C4%83t%C4%83lin_Presentation_CTIEN_License.pdf). If you are interested in the extremely in depth documentation (along with implementation details), please consider the following (100+ pages long) document: [Micea_Radu-Cătălin_Documentation_CTIEN_License.pdf](https://github.com/radumicea/LinguSign/blob/main/Micea_Radu-C%C4%83t%C4%83lin_Documentation_CTIEN_License.pdf)
