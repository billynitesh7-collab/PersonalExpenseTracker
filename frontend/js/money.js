/* Small decimal arithmetic helper for currency values; no floating-point math is used. */
(function () {
  const TEN = 10n;

  function parse(value) {
    const text = String(value ?? "0").trim();
    const match = text.match(/^([+-]?)(\d+)(?:\.(\d*))?(?:[eE]([+-]?\d+))?$/);
    if (!match) throw new TypeError("Invalid decimal amount.");

    const sign = match[1] === "-" ? -1n : 1n;
    const fraction = match[3] || "";
    const exponent = Number(match[4] || 0);
    let coefficient = BigInt(`${match[2]}${fraction}`) * sign;
    let scale = fraction.length - exponent;
    if (scale < 0) {
      coefficient *= TEN ** BigInt(-scale);
      scale = 0;
    }
    return normalize(coefficient, scale);
  }

  function normalize(coefficient, scale) {
    if (coefficient === 0n) return { coefficient: 0n, scale: 0 };
    while (scale > 0 && coefficient % TEN === 0n) {
      coefficient /= TEN;
      scale -= 1;
    }
    return { coefficient, scale };
  }

  function align(left, right) {
    const scale = Math.max(left.scale, right.scale);
    return [
      left.coefficient * TEN ** BigInt(scale - left.scale),
      right.coefficient * TEN ** BigInt(scale - right.scale),
      scale
    ];
  }

  function plain(decimal) {
    const value = normalize(decimal.coefficient, decimal.scale);
    const negative = value.coefficient < 0n;
    let digits = (negative ? -value.coefficient : value.coefficient).toString();
    if (value.scale === 0) return `${negative ? "-" : ""}${digits}`;
    digits = digits.padStart(value.scale + 1, "0");
    const split = digits.length - value.scale;
    return `${negative ? "-" : ""}${digits.slice(0, split)}.${digits.slice(split)}`;
  }

  function add(leftValue, rightValue) {
    const [left, right, scale] = align(parse(leftValue), parse(rightValue));
    return plain(normalize(left + right, scale));
  }

  function subtract(leftValue, rightValue) {
    const [left, right, scale] = align(parse(leftValue), parse(rightValue));
    return plain(normalize(left - right, scale));
  }

  function multiply(leftValue, rightValue) {
    const left = parse(leftValue);
    const right = parse(rightValue);
    return plain(normalize(left.coefficient * right.coefficient, left.scale + right.scale));
  }

  function compare(leftValue, rightValue) {
    const [left, right] = align(parse(leftValue), parse(rightValue));
    return left < right ? -1 : left > right ? 1 : 0;
  }

  function absolute(value) {
    const decimal = parse(value);
    return plain(normalize(decimal.coefficient < 0n ? -decimal.coefficient : decimal.coefficient, decimal.scale));
  }

  function divideRounded(numerator, denominator, decimalPlaces) {
    const left = parse(numerator);
    const right = parse(denominator);
    if (right.coefficient === 0n) throw new RangeError("Cannot divide by zero.");

    let top = left.coefficient * TEN ** BigInt(decimalPlaces + right.scale);
    let bottom = right.coefficient * TEN ** BigInt(left.scale);
    if (bottom < 0n) {
      top = -top;
      bottom = -bottom;
    }
    let quotient = top / bottom;
    const remainder = top % bottom;
    if ((remainder < 0n ? -remainder : remainder) * 2n >= bottom) {
      quotient += top < 0n ? -1n : 1n;
    }
    return plain(normalize(quotient, decimalPlaces));
  }

  function round(value, decimalPlaces = 0) {
    const decimal = parse(value);
    if (decimal.scale <= decimalPlaces) return plain(decimal);
    const divisor = TEN ** BigInt(decimal.scale - decimalPlaces);
    let quotient = decimal.coefficient / divisor;
    const remainder = decimal.coefficient % divisor;
    if ((remainder < 0n ? -remainder : remainder) * 2n >= divisor) {
      quotient += decimal.coefficient < 0n ? -1n : 1n;
    }
    return plain(normalize(quotient, decimalPlaces));
  }

  function sum(values) {
    return values.reduce((total, value) => add(total, value), "0");
  }

  function groupedInteger(digits) {
    return new Intl.NumberFormat("en-IN", { maximumFractionDigits: 0 }).format(BigInt(digits || "0"));
  }

  function fixedParts(value, decimalPlaces) {
    const rounded = parse(round(value, decimalPlaces));
    const negative = rounded.coefficient < 0n;
    let digits = (negative ? -rounded.coefficient : rounded.coefficient).toString();
    if (rounded.scale < decimalPlaces) digits += "0".repeat(decimalPlaces - rounded.scale);
    digits = digits.padStart(decimalPlaces + 1, "0");
    const split = digits.length - decimalPlaces;
    return {
      negative,
      integer: digits.slice(0, split),
      fraction: decimalPlaces ? digits.slice(split).padEnd(decimalPlaces, "0") : ""
    };
  }

  function formatINR(value, compact = false) {
    const decimal = parse(value);
    if (compact && compare(absolute(value), "1000") >= 0) {
      const compactUnits = [["10000000", "Cr"], ["100000", "L"], ["1000", "K"]];
      const [divisor, suffix] = compactUnits.find(([threshold]) => compare(absolute(value), threshold) >= 0);
      const compactValue = fixedParts(divideRounded(absolute(value), divisor, 1), 1);
      return `${decimal.coefficient < 0n ? "-" : ""}₹${groupedInteger(compactValue.integer)}${compactValue.fraction ? `.${compactValue.fraction}` : ""}${suffix}`;
    }
    const parts = fixedParts(value, 2);
    return `${parts.negative ? "-" : ""}₹${groupedInteger(parts.integer)}.${parts.fraction}`;
  }

  function formatPercent(value, places = 2) {
    const parts = fixedParts(value, places);
    return `${parts.negative ? "-" : ""}${groupedInteger(parts.integer)}${parts.fraction ? `.${parts.fraction}` : ""}`;
  }

  function percentOf(value, base, places = 2) {
    if (compare(base, "0") === 0) return "0";
    return divideRounded(multiply(value, "100"), plain(parse(base)), places);
  }

  // Used only to map already exact percentages/amounts to pixels and chart coordinates.
  function visualNumber(value) {
    return Number(plain(parse(value)));
  }

  window.FinanceDecimal = {
    normalize: (value) => plain(parse(value)),
    add,
    subtract,
    multiply,
    compare,
    absolute,
    divideRounded,
    round,
    sum,
    formatINR,
    formatPercent,
    percentOf,
    visualNumber
  };
})();
