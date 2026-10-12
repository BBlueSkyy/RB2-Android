#pragma once

#include <cstdint>

namespace thps::touch {
void Initialize();
void Shutdown();
void Enable(bool enabled);
void State(uint32_t buttons, float lx, float ly, float rx, float ry, float lt, float rt);
void Reset();
}  // namespace thps::touch
