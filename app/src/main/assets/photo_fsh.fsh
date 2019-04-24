// Photo fragment shader. It applies filters on texture.

// This extension is necessary to use samplerExternalOES.
#extension GL_OES_EGL_image_external : require
precision highp float;

// Camera preview texture that needs filter.
uniform samplerExternalOES u_TextureUnit;

// Coordinates of the pixel on the image.
varying vec2 v_TexCoord;


// LUTs

// 3D lookup table to apply.
uniform sampler2D u_TextureLookupTable;

// Number of looks side by side in the texture.
uniform float u_LooksCount;

// Index of look we want to use (for single tile view only).
uniform float u_LookIndex;

// Whether the look should be treated as grayscale.
uniform float u_LookIsGrayscale[9];

// Whether to show nine tile or single tile look.
uniform float u_ShowNineTiles;

//lut 的size是17*17的方格
// Lookup size in each of the three dimensions.
const float kLookupSize = 17.0;

vec3 ApplyLookup(vec3 color,
                 sampler2D lookup_table,
                 float lut_index,
                 float is_grayscale,
                 float luts_count) {
    //color是预览纹理的rgb采样，截断到0-1
  vec3 clamped = clamp(color, vec3(0.0), vec3(1.0));
    //计算颜色在lut方格上的坐标， 【0,16】
  float blue_coord = (kLookupSize - 1.0) * clamped.b;
    //向下取整，截断到【0,15】
  float blue_coord_low = clamp(floor(blue_coord), 0.0, kLookupSize - 2.0);

  float lower_y =
      (0.5 + blue_coord_low * kLookupSize + clamped.g * (kLookupSize - 1.0)) /
      (kLookupSize * kLookupSize);
  float upper_y = lower_y + 1.0 / kLookupSize;

  float x = 0.5 + kLookupSize * lut_index + clamped.r * (kLookupSize - 1.0);
  x = (x + kLookupSize * lut_index) / 2.0;

  x /= kLookupSize * luts_count;
  vec3 lower_rgb = texture2D(lookup_table, vec2(x, lower_y)).rgb;
  vec3 upper_rgb = texture2D(lookup_table, vec2(x, upper_y)).rgb;
  float frac_b = blue_coord - blue_coord_low;

  if (is_grayscale > 0.5) {
    color = vec3(0.3 * color.r + 0.59 * color.g + 0.11 * color.b);
  }
    //线性组合x*a+(1-a)*y,这里为什么不直接return mix(lower_rgb, upper_rgb, frac_b)？
  return mix(color, mix(lower_rgb, upper_rgb, frac_b), 1.0);
}

// Retrieve the index of the filter we should apply to this texture coordinate.
// Because of how we orient the camera, the texture coordinates are rotated in the following way:
// (0, 1) ---- (0, 0)
//   |           |
//   |           |
// (1, 1) ---- (1, 0)
// We want the filter indices to be organized in the following matter:
// 6, 7, 8
// 3, 4, 5
// 0, 1, 2
float GetLutIndex(vec2 texture_coordinate) {
  float xPosScaled = clamp(floor(texture_coordinate.x * 3.0), 0.0, 2.0);
  float yPosScaled = clamp(floor(texture_coordinate.y * 3.0), 0.0, 2.0);
  return (2.0 - xPosScaled) * 3.0 + (2.0 - yPosScaled);
}

// Scales the texture coordinate so that we get the nine-tile look.
vec2 getScaledCoordinates(vec2 texture_coordinates) {
  float xPosScaled = texture_coordinates.x * 3.0;
  float yPosScaled = texture_coordinates.y * 3.0;

  xPosScaled = xPosScaled - clamp(floor(xPosScaled), 0.0, 2.0);
  yPosScaled = yPosScaled - clamp(floor(yPosScaled), 0.0, 2.0);
  return vec2(xPosScaled, yPosScaled);
}

void main() {
  // Values for single tile view.
  vec2 relativeTextureCoordinates = v_TexCoord;
  float filterIndex = u_LookIndex;

  // Values for nine tile view.
  if (u_ShowNineTiles > 0.5) {
    relativeTextureCoordinates = getScaledCoordinates(v_TexCoord);
    filterIndex = GetLutIndex(v_TexCoord);
  }

  vec3 color = texture2D(u_TextureUnit, relativeTextureCoordinates).rgb;
  // Only apply filter if filterIndex is valid.
  if (filterIndex >= 0.0) {
    color = ApplyLookup(color,
                        u_TextureLookupTable,
                        filterIndex,
                        u_LookIsGrayscale[int(filterIndex)],
                        u_LooksCount);
  }
  gl_FragColor = vec4(color, 1.0);
}
