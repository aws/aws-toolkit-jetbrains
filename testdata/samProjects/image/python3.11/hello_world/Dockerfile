FROM public.ecr.aws/lambda/python:3.11

COPY app.py requirements.txt ./

RUN python3.11 -m pip install -r requirements.txt

ENV _HANDLER="app.lambda_handler"

# Command can be overwritten by providing a different command in the template directly.
CMD ["app.lambda_handler"]
