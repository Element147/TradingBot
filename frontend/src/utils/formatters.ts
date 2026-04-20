/**
 * Formatting Utilities
 * 
 * Pure functions for formatting numbers, dates, currencies, and percentages.
 * Used throughout the application for consistent data display.
 */

/**
 * Format a number as currency (USD)
 * 
 * @param value - Numeric value or string representation
 * @param decimals - Number of decimal places (default: 2)
 * @returns Formatted currency string (e.g., "$1,234.56")
 */
export const formatCurrency = (value: string | number, decimals: number = 2): string => {
  const numValue = typeof value === 'string' ? parseFloat(value) : value;
  
  if (isNaN(numValue)) {
    return '$0.00';
  }

  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(numValue);
};

/**
 * Format a number as percentage
 * 
 * @param value - Numeric value or string representation (e.g., "5.25" for 5.25%)
 * @param decimals - Number of decimal places (default: 2)
 * @returns Formatted percentage string (e.g., "5.25%")
 */
export const formatPercentage = (value: string | number, decimals: number = 2): string => {
  const numValue = typeof value === 'string' ? parseFloat(value) : value;
  
  if (isNaN(numValue)) {
    return '0.00%';
  }

  return `${numValue.toFixed(decimals)}%`;
};

/**
 * Format a date/time string for display
 * 
 * @param dateString - ISO 8601 date string
 * @param includeTime - Whether to include time (default: true)
 * @returns Formatted date string (e.g., "Mar 9, 2026, 3:45 PM")
 */
export const formatDateTime = (dateString: string, includeTime: boolean = true): string => {
  try {
    const date = new Date(dateString);
    
    if (isNaN(date.getTime())) {
      return 'Invalid date';
    }

    const options: Intl.DateTimeFormatOptions = {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    };

    if (includeTime) {
      options.hour = 'numeric';
      options.minute = '2-digit';
      options.hour12 = true;
    }

    return new Intl.DateTimeFormat('en-US', options).format(date);
  } catch {
    return 'Invalid date';
  }
};

/**
 * Format a date string (without time)
 * 
 * @param dateString - ISO 8601 date string
 * @returns Formatted date string (e.g., "Mar 9, 2026")
 */
export const formatDate = (dateString: string): string => formatDateTime(dateString, false);

/**
 * Format a number with thousands separators
 * 
 * @param value - Numeric value or string representation
 * @param decimals - Number of decimal places (default: 0)
 * @returns Formatted number string (e.g., "1,234.56")
 */
export const formatNumber = (value: string | number, decimals: number = 0): string => {
  const numValue = typeof value === 'string' ? parseFloat(value) : value;
  
  if (isNaN(numValue)) {
    return '0';
  }

  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(numValue);
};

/**
 * Format a number with thousands separators and trimmed trailing zeroes
 *
 * @param value - Numeric value or string representation
 * @param maximumDecimals - Maximum decimal places to show (default: 4)
 * @param minimumDecimals - Minimum decimal places to show (default: 0)
 * @returns Formatted number string (e.g., "1,234.5", "0.015", "42")
 */
export const formatCompactNumber = (
  value: string | number,
  maximumDecimals: number = 4,
  minimumDecimals: number = 0
): string => {
  const numValue = typeof value === 'string' ? parseFloat(value) : value;

  if (isNaN(numValue)) {
    return '0';
  }

  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: minimumDecimals,
    maximumFractionDigits: maximumDecimals,
  }).format(numValue);
};

/**
 * Format a duration in milliseconds to human-readable string
 * 
 * @param durationMs - Duration in milliseconds
 * @returns Formatted duration (e.g., "2h 30m", "45m", "30s")
 */
export const formatDuration = (durationMs: number): string => {
  const seconds = Math.floor(durationMs / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);

  if (days > 0) {
    return `${days}d ${hours % 24}h`;
  }
  if (hours > 0) {
    return `${hours}h ${minutes % 60}m`;
  }
  if (minutes > 0) {
    return `${minutes}m`;
  }
  return `${seconds}s`;
};

/**
 * Truncate a string to a maximum length with ellipsis
 * 
 * @param str - String to truncate
 * @param maxLength - Maximum length (default: 50)
 * @returns Truncated string with ellipsis if needed
 */
export const truncate = (str: string, maxLength: number = 50): string => {
  if (str.length <= maxLength) {
    return str;
  }
  return `${str.substring(0, maxLength - 3)}...`;
};

/**
 * Format the distance from a date to now in human-readable format
 * 
 * @param date - Date to calculate distance from
 * @returns Formatted distance string (e.g., "2 minutes ago", "1 hour ago", "just now")
 */
export const formatDistanceToNow = (date: Date): string => {
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffSeconds = Math.floor(diffMs / 1000);
  const diffMinutes = Math.floor(diffSeconds / 60);
  const diffHours = Math.floor(diffMinutes / 60);
  const diffDays = Math.floor(diffHours / 24);

  if (diffSeconds < 10) {
    return 'just now';
  }
  if (diffSeconds < 60) {
    return `${diffSeconds} seconds ago`;
  }
  if (diffMinutes === 1) {
    return '1 minute ago';
  }
  if (diffMinutes < 60) {
    return `${diffMinutes} minutes ago`;
  }
  if (diffHours === 1) {
    return '1 hour ago';
  }
  if (diffHours < 24) {
    return `${diffHours} hours ago`;
  }
  if (diffDays === 1) {
    return '1 day ago';
  }
  return `${diffDays} days ago`;
};
