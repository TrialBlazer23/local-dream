#include <algorithm>
#include <cmath>
#include <cstring>
#include <fstream>
#include <iostream>
#include <string>
#include <unordered_map>
#include <vector>

#include "SDStructure.hpp"
#include "SafeTensorReader.hpp"

struct Shape {
  std::vector<int> dims;
  Shape(const std::string& shape_str) {
    if (shape_str.empty()) return;
    int pos = 0;
    int next = 0;
    while ((next = shape_str.find('x', pos)) != std::string::npos) {
      dims.push_back(std::stoi(shape_str.substr(pos, next - pos)));
      pos = next + 1;
    }
    dims.push_back(std::stoi(shape_str.substr(pos)));
  }
};

// std::vector<uint8_t> readConstFile(const std::string& filename) {
//   std::ifstream file(filename, std::ios::binary);
//   if (!file) return {};
//   file.seekg(0, std::ios::end);
//   int size = file.tellg();
//   file.seekg(0, std::ios::beg);
//   std::vector<uint8_t> data(size);
//   file.read(reinterpret_cast<char*>(data.data()), size);
//   return data;
// }

std::vector<uint8_t> fillBuffer(const std::vector<uint32_t>& values,
                                int need_bits) {
  if (need_bits == 8) {
    std::vector<uint8_t> result(values.size());
    for (int i = 0; i < values.size(); ++i) {
      result[i] = static_cast<uint8_t>(values[i]);
    }
    return result;
  }

  int total_bits = values.size() * need_bits;
  int buf_len = (total_bits + 7) / 8;
  std::vector<uint8_t> buffer(buf_len, 0);

  uint32_t mask = (1U << need_bits) - 1;
  int bit_offset = 0;

  for (uint32_t value : values) {
    value &= mask;
    int byte_pos = bit_offset / 8;
    int bit_pos_in_byte = bit_offset % 8;
    int bits_in_current_byte = 8 - bit_pos_in_byte;

    if (need_bits <= bits_in_current_byte) {
      int shift = bits_in_current_byte - need_bits;
      buffer[byte_pos] |= static_cast<uint8_t>(value << shift);
    } else {
      uint32_t high_bits = value >> (need_bits - bits_in_current_byte);
      buffer[byte_pos] |= static_cast<uint8_t>(high_bits);
      int remaining_bits = need_bits - bits_in_current_byte;
      uint32_t low_bits = value & ((1U << remaining_bits) - 1);
      int shift = 8 - remaining_bits;
      buffer[byte_pos + 1] |= static_cast<uint8_t>(low_bits << shift);
    }

    bit_offset += need_bits;
  }

  return buffer;
}

std::vector<uint8_t> quantizeWeights(const std::vector<float>& weights,
                                     const Shape& shape) {
  if (shape.dims.size() != 4) return {};

  int oc = shape.dims[0], ic = shape.dims[1], h = shape.dims[2],
      w = shape.dims[3];
  int kxky = h * w;
  int kernel_size = ic * kxky;
  int block_size = 32;
  float threshold = 127.0f;

  int block_num = 1;
  int actual_block_size = kernel_size;

  if (block_size > 0 && (ic % block_size == 0) && block_size >= 16 &&
      (block_size % 16 == 0)) {
    block_num = ic / block_size;
    actual_block_size = block_size * kxky;
  }

  std::vector<float> scales(oc * block_num);
  for (int k = 0; k < oc; ++k) {
    for (int b = 0; b < block_num; ++b) {
      int begin_index = b * actual_block_size;
      int end_index = begin_index + actual_block_size;

      float abs_max = 0.0f;
      for (int idx = begin_index; idx < end_index; ++idx) {
        int weight_idx = k * kernel_size + idx;
        abs_max = std::max(abs_max, std::abs(weights[weight_idx]));
      }

      scales[k * block_num + b] = abs_max / threshold;
    }
  }

  int offset = 128;
  int min_value = -128;
  int max_value = 127;
  int value_count = 256;
  int need_bits = 8;

  std::vector<uint32_t> indices;
  indices.reserve(weights.size());

  for (int k = 0; k < oc; ++k) {
    for (int b = 0; b < block_num; ++b) {
      int begin_index = b * actual_block_size;
      int end_index = begin_index + actual_block_size;
      float scale = scales[k * block_num + b];

      for (int idx = begin_index; idx < end_index; ++idx) {
        int weight_idx = k * kernel_size + idx;
        float ratio = (scale > 1e-6f) ? weights[weight_idx] / scale : 0.0f;
        int value = static_cast<int>(std::round(ratio));
        value = std::max(min_value, std::min(max_value, value));
        indices.push_back(static_cast<uint32_t>(value + offset));
      }
    }
  }

  std::vector<uint8_t> result;

  std::vector<int> blob_dims = {oc * block_num, actual_block_size};
  result.push_back(static_cast<uint8_t>(blob_dims.size()));
  bool use_int32 = std::any_of(blob_dims.begin(), blob_dims.end(),
                               [](int d) { return d > 65535; });

  if (use_int32) {
    for (int dim : blob_dims) {
      uint32_t d = static_cast<uint32_t>(dim);
      const uint8_t* bytes = reinterpret_cast<const uint8_t*>(&d);
      result.insert(result.end(), bytes, bytes + 4);
    }
  } else {
    for (int dim : blob_dims) {
      uint16_t d = static_cast<uint16_t>(dim);
      const uint8_t* bytes = reinterpret_cast<const uint8_t*>(&d);
      result.insert(result.end(), bytes, bytes + 2);
    }
  }

  result.push_back(static_cast<uint8_t>(value_count));
  for (int value = min_value; value <= max_value; ++value) {
    result.push_back(static_cast<uint8_t>(value));
  }

  std::vector<uint8_t> compressed = fillBuffer(indices, need_bits);
  result.insert(result.end(), compressed.begin(), compressed.end());

  const uint8_t* scale_bytes = reinterpret_cast<const uint8_t*>(scales.data());
  result.insert(result.end(), scale_bytes,
                scale_bytes + scales.size() * sizeof(float));

  return result;
}

void generateModel(const std::string& dir, const std::string& safetensor_file,
                   const std::string& model_name,
                   const std::vector<std::vector<std::string>>& structure) {
  SafeTensorReader reader(dir + "/" + safetensor_file);
  std::ofstream weight_file(dir + "/model.mnn.weight", std::ios::binary);

  for (const auto& weight_info : structure) {
    const std::string& weight_name = weight_info[0];
    const std::string& data_type = weight_info[1];

    if (data_type == "fp32") {
      reader.read(weight_name);
      weight_file.write(reinterpret_cast<const char*>(reader.data.data()),
                        reader.data.size() * sizeof(float));
    } else if (data_type == "fp16") {
      reader.read(weight_name, false);
      weight_file.write(reinterpret_cast<const char*>(reader.fp16_data.data()),
                        reader.fp16_data.size() * sizeof(uint16_t));
    } else if (data_type == "const") {
      // int const_idx = std::stoi(weight_info[2]);
      int zero_length = std::stoi(weight_info[2]);
      // std::cout << zero_length << std::endl;
      // std::string const_filename =
      //     dir + "/const/const_" + std::to_string(const_idx) + ".bin";
      // auto const_data = readConstFile(const_filename);
      std::vector<float> const_data(zero_length, 0.0f);
      weight_file.write(reinterpret_cast<const char*>(const_data.data()),
                        const_data.size() * sizeof(float));
    } else if (data_type == "block_quant") {
      reader.read(weight_name);
      Shape shape(weight_info[2]);
      auto quantized = quantizeWeights(reader.data, shape);
      weight_file.write(reinterpret_cast<const char*>(quantized.data()),
                        quantized.size());
    }
  }
  weight_file.close();

  std::string final_name = dir + "/" + model_name + ".mnn.weight";
  std::rename((dir + "/model.mnn.weight").c_str(), final_name.c_str());
}

void patchModel(const std::string& dir, const std::string& safetensor_file,
                const std::string& model_name,
                const std::unordered_map<std::string, int>& small_weights,
                bool fp16 = false) {
  std::string mnn_filepath = dir + "/" + model_name + ".mnn";

  std::fstream mnn_file(mnn_filepath,
                        std::ios::in | std::ios::out | std::ios::binary);

  SafeTensorReader reader(dir + "/" + safetensor_file);

  for (const auto& pair : small_weights) {
    const std::string& weight_name = pair.first;
    int offset = pair.second;

    if (fp16) {
      reader.read(weight_name, false);
    } else {
      reader.read(weight_name);
    }

    int data_size_bytes;
    if (fp16) {
      data_size_bytes = reader.fp16_data.size() * sizeof(uint16_t);
    } else {
      data_size_bytes = reader.data.size() * sizeof(float);
    }

    mnn_file.seekp(offset, std::ios::beg);
    if (!mnn_file) {
      mnn_file.close();
      return;
    }

    if (fp16) {
      mnn_file.write(reinterpret_cast<const char*>(reader.fp16_data.data()),
                     data_size_bytes);
    } else {
      mnn_file.write(reinterpret_cast<const char*>(reader.data.data()),
                     data_size_bytes);
    }
  }
  mnn_file.close();
}

void generateClipModel(const std::string& dir,
                       const std::string& safetensor_file,
                       bool clip_skip_2 = false) {
  if (clip_skip_2) {
    generateModel(dir, safetensor_file, "clip", clip_skip_2_structure);
  } else {
    generateModel(dir, safetensor_file, "clip", clip_structure);
  }

  int header_size = 246656;
  int middle_size = 2256;
  if (clip_skip_2) {
    header_size = 167888;
    middle_size = 888;
  }

  auto filename = dir + "/clip.mnn.slimmed";
  if (clip_skip_2) {
    filename = dir + "/clip_skip_2.mnn.slimmed";
  }

  std::ifstream slimmed_file(filename, std::ios::binary);
  slimmed_file.seekg(0, std::ios::end);
  int slimmed_size = slimmed_file.tellg();
  slimmed_file.seekg(0, std::ios::beg);
  std::vector<uint8_t> slimmed_data(slimmed_size);
  slimmed_file.read(reinterpret_cast<char*>(slimmed_data.data()), slimmed_size);
  slimmed_file.close();

  SafeTensorReader reader(dir + "/" + safetensor_file);

  reader.read(
      "cond_stage_model.transformer.text_model.embeddings.position_embedding."
      "weight",
      false);
  std::vector<uint8_t> pos_emb_bytes(reader.fp16_data.size() *
                                     sizeof(uint16_t));
  std::memcpy(pos_emb_bytes.data(), reader.fp16_data.data(),
              pos_emb_bytes.size());

  reader.read(
      "cond_stage_model.transformer.text_model.embeddings.token_embedding."
      "weight",
      false);
  std::vector<uint8_t> token_emb_bytes(reader.fp16_data.size() *
                                       sizeof(uint16_t));
  std::memcpy(token_emb_bytes.data(), reader.fp16_data.data(),
              token_emb_bytes.size());

  std::vector<uint8_t> header(slimmed_data.begin(),
                              slimmed_data.begin() + header_size);
  std::vector<uint8_t> middle(slimmed_data.begin() + header_size,
                              slimmed_data.begin() + header_size + middle_size);
  std::vector<uint8_t> tail(slimmed_data.begin() + header_size + middle_size,
                            slimmed_data.end());

  std::ofstream output_file(dir + "/clip.mnn", std::ios::binary);
  output_file.write(reinterpret_cast<const char*>(header.data()),
                    header.size());
  output_file.write(reinterpret_cast<const char*>(pos_emb_bytes.data()),
                    pos_emb_bytes.size());
  output_file.write(reinterpret_cast<const char*>(middle.data()),
                    middle.size());
  output_file.write(reinterpret_cast<const char*>(token_emb_bytes.data()),
                    token_emb_bytes.size());
  output_file.write(reinterpret_cast<const char*>(tail.data()), tail.size());
  output_file.close();
}

void generateMNNModels(const std::string& dir,
                       const std::string& safetensor_file,
                       bool clip_skip_2 = false) {
  std::cout << "Generating CLIP model..." << std::endl;
  generateClipModel(dir, safetensor_file, clip_skip_2);

  std::cout << "Generating UNet model..." << std::endl;
  generateModel(dir, safetensor_file, "unet", unet_structure);
  patchModel(dir, safetensor_file, "unet", unet_small_weights);

  std::cout << "Generating VAE Decoder model..." << std::endl;
  generateModel(dir, safetensor_file, "vae_decoder", vae_decoder_structure);
  patchModel(dir, safetensor_file, "vae_decoder", vae_decoder_small_weights,
             true);

  std::cout << "Generating VAE Encoder model..." << std::endl;
  generateModel(dir, safetensor_file, "vae_encoder", vae_encoder_structure);
  patchModel(dir, safetensor_file, "vae_encoder", vae_encoder_small_weights,
             true);

  std::ofstream finished_file(dir + "/finished");
  finished_file.close();

  std::cout << "All models generated successfully!" << std::endl;
}
