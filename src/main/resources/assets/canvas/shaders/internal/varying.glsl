#include frex:shaders/api/context.glsl
#include canvas:shaders/internal/context.glsl

/******************************************************
  canvas:shaders/internal/varying.glsl
******************************************************/

#if AO_SHADING_MODE != AO_MODE_NONE
varying float _cvv_ao;
#endif

#if DIFFUSE_SHADING_MODE != DIFFUSE_MODE_NONE
varying float _cvv_diffuse;
#endif

varying vec4 _cvv_color;
varying vec2 _cvv_texcoord;
varying vec2 _cvv_lightcoord;
varying vec3 _cvv_normal;
varying vec3 _cvv_worldcoord;
