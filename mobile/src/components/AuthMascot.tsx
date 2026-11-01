import React from 'react';
import { View } from 'react-native';
import Svg, {
  Circle,
  ClipPath,
  Defs,
  Ellipse,
  G,
  Path,
  RadialGradient,
  Rect,
  Stop,
} from 'react-native-svg';

/** Vector port of the supplied Flutter CustomPainter, not the former small avatar.
 * Event-driven poses keep the login worker free of continuous JS animation loops. */
export default function AuthMascot({
  covered,
  size = 150,
  look = 0,
  busy = false,
  unhappy = false,
}: {
  covered: boolean;
  size?: number;
  look?: number;
  busy?: boolean;
  unhappy?: boolean;
}) {
  const x = Math.max(-1, Math.min(1, look));
  const dx = x * 5,
    dy = busy ? -4 : look ? 3 : 0;
  return (
    <View
      style={{ width: size, height: size }}
      accessible={false}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
    >
      <Svg width="100%" height="100%" viewBox="0 0 150 150">
        <Defs>
          <RadialGradient id="skin" cx="32%" cy="25%" r="80%">
            <Stop offset="0" stopColor="#FFE9D2" />
            <Stop offset="1" stopColor="#FFC697" />
          </RadialGradient>
          <ClipPath id="face">
            <Circle cx="75" cy="69" r="49.5" />
          </ClipPath>
        </Defs>
        <Rect x="59" y="103" width="32" height="60" fill="#FFC697" />
        <G rotation={x * 2} origin="75,69">
          {[26.5, 123.5].map((cx) => (
            <G key={cx}>
              <Circle cx={cx} cy="72" r="9" fill="#FFC697" />
              <Circle cx={cx} cy="72" r="4.5" fill="#E29B68" opacity="0.35" />
            </G>
          ))}
          <Circle
            cx="75"
            cy="69"
            r="49.5"
            fill="url(#skin)"
            stroke="#E29B68"
            strokeOpacity="0.5"
            strokeWidth="1.8"
          />
          <G clipPath="url(#face)">
            <Ellipse cx="75" cy="15.5" rx="59.4" ry="23.5" fill="#1B2A41" />
          </G>
          {[42.3, 107.7].map((cx) => (
            <Circle
              key={cx}
              cx={cx}
              cy="83.8"
              r="7.9"
              fill="#FF6F8D"
              opacity="0.34"
            />
          ))}
          {[54.2, 95.8].map((cx) => (
            <G key={cx}>
              <Path
                d={`M${cx - 11.8} 48 Q${cx} ${unhappy ? 41 : 42} ${cx + 11.8} ${unhappy ? 43 : 48}`}
                stroke="#6B4228"
                strokeWidth="2"
                strokeLinecap="round"
                fill="none"
              />
              {covered ? (
                <Path
                  d={`M${cx - 10} 68 Q${cx} 75 ${cx + 10} 68`}
                  stroke="#14213D"
                  strokeWidth="2"
                  fill="none"
                />
              ) : (
                <>
                  <Circle
                    cx={cx}
                    cy="68"
                    r="12.4"
                    fill="white"
                    stroke="#14213D"
                    strokeOpacity="0.25"
                  />
                  <Circle cx={cx + dx} cy={68 + dy} r="6.8" fill="#14213D" />
                  <Circle
                    cx={cx + dx - 2.2}
                    cy={68 + dy - 2.4}
                    r="2"
                    fill="white"
                  />
                </>
              )}
            </G>
          ))}
          <Path
            d={`M63 91 Q75 ${unhappy ? 80 : 105} 87 91`}
            fill="none"
            stroke="#8A3A3A"
            strokeWidth="2.7"
            strokeLinecap="round"
          />
          <Path
            d="M75 31 C54 17 63 4 75 14 C87 4 96 17 75 31Z"
            fill="#E5262F"
          />
          <Circle cx="68" cy="13" r="2" fill="white" opacity="0.7" />
        </G>
        {covered &&
          [54.2, 95.8].map((cx, i) => (
            <G key={cx}>
              <Path
                d={`M${cx} 78 L${i === 0 ? 8 : 142} 166`}
                stroke="#E29B68"
                strokeOpacity="0.3"
                strokeWidth="28"
                strokeLinecap="round"
              />
              <Path
                d={`M${cx} 78 L${i === 0 ? 8 : 142} 166`}
                stroke="white"
                strokeWidth="25"
                strokeLinecap="round"
              />
              <Rect
                x={cx - 19.8}
                y="52"
                width="39.6"
                height="32"
                rx="14.8"
                fill="#FFD0A4"
                stroke="#E29B68"
                strokeWidth="1.8"
              />
              {[-10, 0, 10].map((offset) => (
                <Path
                  key={offset}
                  d={`M${cx + offset} 55 L${cx + offset} 65`}
                  stroke="#E29B68"
                  strokeOpacity="0.7"
                  strokeWidth="1.5"
                  strokeLinecap="round"
                />
              ))}
            </G>
          ))}
      </Svg>
    </View>
  );
}
