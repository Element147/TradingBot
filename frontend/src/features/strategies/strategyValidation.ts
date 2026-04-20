import { z } from 'zod';

export const strategyConfigSchema = z.object({
  symbol: z.string().trim().min(1, 'Symbol is required'),
  timeframe: z.string().trim().min(1, 'Timeframe is required'),
  riskPerTrade: z
    .number({ error: 'Risk per trade must be a valid number' })
    .min(0.01, 'Risk per trade must be between 0.01 and 0.05')
    .max(0.05, 'Risk per trade must be between 0.01 and 0.05'),
  minPositionSize: z
    .number({ error: 'Min position size must be a valid number' })
    .positive('Min position size must be positive'),
  maxPositionSize: z
    .number({ error: 'Max position size must be a valid number' })
    .positive('Max position size must be positive'),
  shortSellingEnabled: z.boolean(),
});

export type StrategyConfigInput = z.input<typeof strategyConfigSchema>;
export type StrategyConfigOutput = z.output<typeof strategyConfigSchema>;

export const isValidRiskPercentage = (riskPerTrade: number): boolean =>
  strategyConfigSchema.shape.riskPerTrade.safeParse(riskPerTrade).success;

export const isValidPositionSize = (size: number): boolean =>
  strategyConfigSchema.shape.minPositionSize.safeParse(size).success;

export const validateStrategyConfig = (
  config: StrategyConfigInput
): {
  valid: boolean;
  errors: Partial<Record<keyof StrategyConfigOutput | 'maxPositionSize', string>>;
  data?: StrategyConfigOutput;
} => {
  const parsed = strategyConfigSchema.safeParse(config);

  if (!parsed.success) {
    const issues = parsed.error.issues.reduce<Partial<Record<keyof StrategyConfigOutput, string>>>(
      (acc, issue) => {
        const key = issue.path[0] as keyof StrategyConfigOutput;
        if (key && !acc[key]) {
          acc[key] = issue.message;
        }
        return acc;
      },
      {}
    );
    return { valid: false, errors: issues };
  }

  if (parsed.data.maxPositionSize < parsed.data.minPositionSize) {
    return {
      valid: false,
      errors: {
        maxPositionSize: 'Max position size must be greater than or equal to min position size',
      },
    };
  }

  return { valid: true, errors: {}, data: parsed.data };
};
