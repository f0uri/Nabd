import { useWindowDimensions } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { size, space, type } from '../theme';

/** Same geometry for the floating dock and the scrollable content underneath it. */
export default function useDockInset() {
  const { bottom } = useSafeAreaInsets();
  const { fontScale } = useWindowDimensions();
  return size.tabBar + space.lg + Math.max(bottom, space.xs) +
    Math.max(0, fontScale - 1) * type.micro.lineHeight;
}
