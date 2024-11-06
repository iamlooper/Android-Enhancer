#include "type_converter_util.hpp"

std::vector<std::string>
TypeConverterUtil::stringToVectorString(const std::string &source) {
    std::vector<std::string> result;
    std::stringstream stream(source);
    std::string line;

    // Process each line of the input string and add it to the result vector
    while (std::getline(stream, line)) {
        result.push_back(line);
    }

    return result;
}