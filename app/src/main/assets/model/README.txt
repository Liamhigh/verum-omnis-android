Drop production ONNX models here before release:
- behavioral.onnx
- voice.onnx
- image.onnx
- video.onnx
(App will run in Rules-Only Mode if these are missing.)

Supported local LLM task packs:
- gemma3-1B-it-int4.task (bundled by default)
- Phi-3-mini-4k-instruct-q4.gguf (optional external offline model pack)

Phi-3 Mini notes:
- Keep the model fully local and hash-verified before release.
- Place the GGUF file in the app external models directory for runtime loading/staging.
- This keeps the 2.2 GB Phi-3 pack outside the APK.
- The current app runtime still uses MediaPipe for Gemma. Phi-3 staging is wired, but GGUF inference needs a separate local runtime bridge before prompts can run through Phi-3 on-device.
- The forensic engine remains deterministic and primary; local LLMs are reviewers, not replacements.
