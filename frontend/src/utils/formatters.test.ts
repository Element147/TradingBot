import { describe, it, expect } from 'vitest';

import {
  formatCompactNumber,
  formatCurrency,
  formatPercentage,
  formatDateTime,
  formatDate,
  formatNumber,
  formatDuration,
  truncate,
  formatDistanceToNow,
} from './formatters';

describe('formatters', () => {
  describe('formatCurrency', () => {
    it('should format number as currency', () => {
      expect(formatCurrency(1234.56)).toBe('$1,234.56');
      expect(formatCurrency(0)).toBe('$0.00');
      expect(formatCurrency(1000000)).toBe('$1,000,000.00');
    });

    it('should format string as currency', () => {
      expect(formatCurrency('1234.56')).toBe('$1,234.56');
      expect(formatCurrency('0')).toBe('$0.00');
    });

    it('should handle custom decimal places', () => {
      expect(formatCurrency(1234.5678, 4)).toBe('$1,234.5678');
      expect(formatCurrency(1234.5, 0)).toBe('$1,235');
    });

    it('should handle invalid input', () => {
      expect(formatCurrency('invalid')).toBe('$0.00');
      expect(formatCurrency(NaN)).toBe('$0.00');
    });

    it('should handle negative values', () => {
      expect(formatCurrency(-1234.56)).toBe('-$1,234.56');
    });
  });

  describe('formatPercentage', () => {
    it('should format number as percentage', () => {
      expect(formatPercentage(5.25)).toBe('5.25%');
      expect(formatPercentage(0)).toBe('0.00%');
      expect(formatPercentage(100)).toBe('100.00%');
    });

    it('should format string as percentage', () => {
      expect(formatPercentage('5.25')).toBe('5.25%');
      expect(formatPercentage('0')).toBe('0.00%');
    });

    it('should handle custom decimal places', () => {
      expect(formatPercentage(5.2567, 4)).toBe('5.2567%');
      expect(formatPercentage(5.2567, 0)).toBe('5%');
    });

    it('should handle invalid input', () => {
      expect(formatPercentage('invalid')).toBe('0.00%');
      expect(formatPercentage(NaN)).toBe('0.00%');
    });

    it('should handle negative values', () => {
      expect(formatPercentage(-5.25)).toBe('-5.25%');
    });
  });

  describe('formatDateTime', () => {
    it('should format ISO date string with time', () => {
      const result = formatDateTime('2026-03-09T15:45:00Z');
      expect(result).toMatch(/Mar 9, 2026/);
      expect(result).toMatch(/PM/);
    });

    it('should format date without time when specified', () => {
      const result = formatDateTime('2026-03-09T15:45:00Z', false);
      expect(result).toBe('Mar 9, 2026');
      expect(result).not.toMatch(/PM/);
    });

    it('should handle invalid date string', () => {
      expect(formatDateTime('invalid')).toBe('Invalid date');
      expect(formatDateTime('')).toBe('Invalid date');
    });
  });

  describe('formatDate', () => {
    it('should format date without time', () => {
      const result = formatDate('2026-03-09T15:45:00Z');
      expect(result).toBe('Mar 9, 2026');
      expect(result).not.toMatch(/PM/);
    });
  });

  describe('formatNumber', () => {
    it('should format number with thousands separators', () => {
      expect(formatNumber(1234)).toBe('1,234');
      expect(formatNumber(1234567)).toBe('1,234,567');
      expect(formatNumber(0)).toBe('0');
    });

    it('should format string as number', () => {
      expect(formatNumber('1234')).toBe('1,234');
      expect(formatNumber('1234567.89')).toBe('1,234,568');
    });

    it('should handle custom decimal places', () => {
      expect(formatNumber(1234.5678, 2)).toBe('1,234.57');
      expect(formatNumber(1234.5, 4)).toBe('1,234.5000');
    });

    it('should handle invalid input', () => {
      expect(formatNumber('invalid')).toBe('0');
      expect(formatNumber(NaN)).toBe('0');
    });
  });

  describe('formatCompactNumber', () => {
    it('should trim trailing zeroes while keeping separators', () => {
      expect(formatCompactNumber(1234.5)).toBe('1,234.5');
      expect(formatCompactNumber('1234.5678')).toBe('1,234.5678');
      expect(formatCompactNumber('1234.5000')).toBe('1,234.5');
    });

    it('should respect maximum decimals', () => {
      expect(formatCompactNumber(1234.56789, 2)).toBe('1,234.57');
      expect(formatCompactNumber('0.0154321', 4)).toBe('0.0154');
    });

    it('should handle invalid input', () => {
      expect(formatCompactNumber('invalid')).toBe('0');
      expect(formatCompactNumber(NaN)).toBe('0');
    });
  });

  describe('formatDuration', () => {
    it('should format duration in days and hours', () => {
      const twoDays = 2 * 24 * 60 * 60 * 1000;
      expect(formatDuration(twoDays)).toBe('2d 0h');
      
      const oneDayThreeHours = (24 + 3) * 60 * 60 * 1000;
      expect(formatDuration(oneDayThreeHours)).toBe('1d 3h');
    });

    it('should format duration in hours and minutes', () => {
      const twoHours = 2 * 60 * 60 * 1000;
      expect(formatDuration(twoHours)).toBe('2h 0m');
      
      const twoHoursThirtyMin = (2 * 60 + 30) * 60 * 1000;
      expect(formatDuration(twoHoursThirtyMin)).toBe('2h 30m');
    });

    it('should format duration in minutes', () => {
      const fortyFiveMin = 45 * 60 * 1000;
      expect(formatDuration(fortyFiveMin)).toBe('45m');
    });

    it('should format duration in seconds', () => {
      const thirtySeconds = 30 * 1000;
      expect(formatDuration(thirtySeconds)).toBe('30s');
    });

    it('should handle zero duration', () => {
      expect(formatDuration(0)).toBe('0s');
    });
  });

  describe('truncate', () => {
    it('should truncate long strings', () => {
      const longString = 'This is a very long string that needs to be truncated';
      expect(truncate(longString, 20)).toBe('This is a very lo...');
    });

    it('should not truncate short strings', () => {
      const shortString = 'Short';
      expect(truncate(shortString, 20)).toBe('Short');
    });

    it('should handle exact length', () => {
      const exactString = 'Exactly twenty chars';
      expect(truncate(exactString, 20)).toBe('Exactly twenty chars');
    });

    it('should use default max length of 50', () => {
      const longString = 'a'.repeat(60);
      const result = truncate(longString);
      expect(result).toHaveLength(50);
      expect(result).toMatch(/\.\.\.$/);
    });
  });

  describe('formatDistanceToNow', () => {
    it('should return "just now" for very recent dates', () => {
      const now = new Date();
      const fiveSecondsAgo = new Date(now.getTime() - 5 * 1000);
      expect(formatDistanceToNow(fiveSecondsAgo)).toBe('just now');
    });

    it('should format seconds ago', () => {
      const now = new Date();
      const thirtySecondsAgo = new Date(now.getTime() - 30 * 1000);
      expect(formatDistanceToNow(thirtySecondsAgo)).toBe('30 seconds ago');
    });

    it('should format single minute ago', () => {
      const now = new Date();
      const oneMinuteAgo = new Date(now.getTime() - 60 * 1000);
      expect(formatDistanceToNow(oneMinuteAgo)).toBe('1 minute ago');
    });

    it('should format multiple minutes ago', () => {
      const now = new Date();
      const fiveMinutesAgo = new Date(now.getTime() - 5 * 60 * 1000);
      expect(formatDistanceToNow(fiveMinutesAgo)).toBe('5 minutes ago');
    });

    it('should format single hour ago', () => {
      const now = new Date();
      const oneHourAgo = new Date(now.getTime() - 60 * 60 * 1000);
      expect(formatDistanceToNow(oneHourAgo)).toBe('1 hour ago');
    });

    it('should format multiple hours ago', () => {
      const now = new Date();
      const threeHoursAgo = new Date(now.getTime() - 3 * 60 * 60 * 1000);
      expect(formatDistanceToNow(threeHoursAgo)).toBe('3 hours ago');
    });

    it('should format single day ago', () => {
      const now = new Date();
      const oneDayAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000);
      expect(formatDistanceToNow(oneDayAgo)).toBe('1 day ago');
    });

    it('should format multiple days ago', () => {
      const now = new Date();
      const threeDaysAgo = new Date(now.getTime() - 3 * 24 * 60 * 60 * 1000);
      expect(formatDistanceToNow(threeDaysAgo)).toBe('3 days ago');
    });
  });
});
