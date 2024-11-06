#pragma once

#include <string>
#include <vector>
#include <sstream>

class TypeConverterUtil {
public:
    /**
     * Converts a value from the source type to the target type
     *
     * @tparam targetType, the type to convert to
     * @tparam sourceType, the type to convert from
     * @param source, the value to convert
     * @return The converted value of the target type
     */
    template<typename targetType, typename sourceType>
    static targetType to(const sourceType &source);

    /**
     * Converts a string into a vector of strings, splitting by line
     *
     * @param source, the input string to convert
     * @return A vector of strings where each element corresponds to a line in the input string
     */
    static std::vector<std::string> stringToVectorString(const std::string &source);
};

// Template implementation
template<typename targetType, typename sourceType>
targetType TypeConverterUtil::to(const sourceType &source) {
    targetType result;
    std::stringstream stream;

    // Convert the source value to a string
    stream << source;

    // Extract the converted value from the stringstream
    stream >> result;

    return result;
}