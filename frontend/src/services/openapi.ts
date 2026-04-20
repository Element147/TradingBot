import type { paths } from '@/generated/openapi';

type PathKey = keyof paths;
type HttpMethod<Path extends PathKey> = Extract<
  keyof paths[Path],
  'get' | 'post' | 'put' | 'delete' | 'patch' | 'options' | 'head'
>;

type Operation<Path extends PathKey, Method extends HttpMethod<Path>> = NonNullable<
  paths[Path][Method]
>;

type ContentValue<T> = T extends { content: infer Content extends Record<string, unknown> }
  ? Content[keyof Content]
  : never;

type RequestBodyValue<T> = T extends { requestBody?: infer Body }
  ? ContentValue<NonNullable<Body>>
  : never;

type ResponseValue<T, Status extends keyof T> = T[Status] extends infer Response
  ? ContentValue<NonNullable<Response>>
  : never;

export type ApiRequestBody<
  Path extends PathKey,
  Method extends HttpMethod<Path>,
> = RequestBodyValue<Operation<Path, Method>>;

export type ApiResponse<
  Path extends PathKey,
  Method extends HttpMethod<Path>,
  Status extends number = 200,
> = Operation<Path, Method> extends { responses: infer Responses }
  ? Status extends keyof Responses
    ? ResponseValue<Responses, Status>
    : never
  : never;
